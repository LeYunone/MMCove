package com.mmcove.agent.tools.registry;

import com.mmcove.agent.tools.external.annotation.DangerousOperation;
import com.mmcove.agent.tools.external.annotation.ParamSpec;
import com.mmcove.agent.tools.external.annotation.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动扫描 @Tool 注解的 Bean，统一注册为 Spring AI ToolCallback。
 * <p>
 * 对标记了 @DangerousOperation 的 @Tool 方法，自动在工具描述后追加确认提示。
 * 支持通过 ToolOverrideProvider 动态覆盖工具描述、参数 Schema 和危险标记。
 */
@Slf4j
@Component
public class ToolRegistry implements BeanPostProcessor {

    private final List<ToolCallback> toolCallbacks = new ArrayList<>();

    /** 工具来源类名映射：toolName -> sourceClassName */
    private final ConcurrentHashMap<String, String> toolSourceClasses = new ConcurrentHashMap<>();

    /** 标记了 @DangerousOperation 的原始工具名集合 */
    private final Set<String> originallyDangerousToolNames = new HashSet<>();

    @Autowired(required = false)
    private ToolOverrideProvider overrideProvider;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (hasToolAnnotatedMethods(bean)) {
            registerToolMethods(bean);
        }
        return bean;
    }

    private boolean hasToolAnnotatedMethods(Object bean) {
        return Arrays.stream(bean.getClass().getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(Tool.class));
    }

    private void registerToolMethods(Object bean) {
        // 收集标记了 @DangerousOperation 的方法名 + 其风险级别 + 建 @Tool 方法名→Method 映射(供 ParamSpec 反射)
        Map<String, RiskLevel> dangerousLevels = new HashMap<>();
        Map<String, Method> toolMethodMap = new HashMap<>();
        for (Method method : bean.getClass().getDeclaredMethods()) {
            DangerousOperation dangerous = method.getAnnotation(DangerousOperation.class);
            if (dangerous != null) {
                dangerousLevels.put(method.getName(), dangerous.level());
            }
            if (method.isAnnotationPresent(Tool.class)) {
                toolMethodMap.put(method.getName(), method);
            }
        }

        String sourceClass = bean.getClass().getSimpleName();

        try {
            ToolCallback[] callbacks = ToolCallbacks.from(bean);
            for (ToolCallback callback : callbacks) {
                ToolCallback registered = callback;
                String toolName = callback.getToolDefinition().name();
                // 「精准防飘移」L5:ParamSpec 校验装饰器(中层,仅对带 @ParamSpec 的方法生效,无注解零副作用)
                Method toolMethod = toolMethodMap.get(toolName);
                if (toolMethod != null && hasParamSpec(toolMethod)) {
                    registered = new ParamSpecToolCallback(callback, toolMethod);
                }
                // 「精准防飘移」L6:风险四级分级,仅 DESTRUCTIVE/PRIVILEGED 追加确认提示(READ/REVERSIBLE 直接执行)
                RiskLevel riskLevel = dangerousLevels.get(toolName);
                if (riskLevel != null) {
                    registered = new DangerousOperationToolCallback(registered, riskLevel);
                    // 仅不可逆/特权级计入"危险集合"(供熔断只读过滤/DB dangerous 标记),REVERSIBLE/READ 可逆免过滤
                    if (riskLevel == RiskLevel.DESTRUCTIVE || riskLevel == RiskLevel.PRIVILEGED) {
                        originallyDangerousToolNames.add(toolName);
                    }
                }
                toolCallbacks.add(registered);
                toolSourceClasses.put(toolName, sourceClass);
                log.info("已注册 @Tool 方法: {}{}", toolName,
                        registered != callback ? " [装饰器]" : "");
            }
        } catch (Exception e) {
            log.warn("注册 @Tool 方法失败 [{}]: {}", bean.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * 方法的参数是否标注了 @ParamSpec(决定是否套 ParamSpecToolCallback)。
     */
    private boolean hasParamSpec(Method method) {
        for (Parameter p : method.getParameters()) {
            if (p.isAnnotationPresent(ParamSpec.class)
                    || p.isAnnotationPresent(ParamSpec.Container.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取工具回调列表，应用覆盖配置。
     * 有覆盖配置时，用 DynamicDescriptionToolCallback 包装原始回调。
     */
    public List<ToolCallback> getToolCallbacks() {
        if (overrideProvider == null) {
            return List.copyOf(toolCallbacks);
        }
        List<ToolCallback> result = new ArrayList<>(toolCallbacks.size());
        for (ToolCallback callback : toolCallbacks) {
            String toolName = callback.getToolDefinition().name();
            result.add(applyOverride(callback, toolName));
        }
        return result;
    }

    private ToolCallback applyOverride(ToolCallback callback, String toolName) {
        if (overrideProvider == null) {
            return callback;
        }
        var overrideOpt = overrideProvider.getOverride(toolName);
        if (overrideOpt.isPresent()) {
            return new DynamicDescriptionToolCallback(callback, overrideOpt.get());
        }
        return callback;
    }

    /**
     * 按工具名集合过滤工具回调列表。
     * 如果传入的集合为空或 null，返回空列表。
     */
    public List<ToolCallback> getToolCallbacksByNames(Set<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }

        List<ToolCallback> result = new ArrayList<>();
        for (ToolCallback callback : toolCallbacks) {
            String toolName = callback.getToolDefinition().name();
            if (toolNames.contains(toolName)) {
                ToolCallback finalCallback = applyOverride(callback, toolName);
                result.add(finalCallback);
            }
        }
        return result;
    }

    /**
     * 获取原始工具回调列表（不应用覆盖配置，供同步使用）。
     */
    public List<ToolCallback> getOriginalToolCallbacks() {
        return List.copyOf(toolCallbacks);
    }

    /**
     * 获取工具来源类映射。
     */
    public Map<String, String> getToolSourceClasses() {
        return Map.copyOf(toolSourceClasses);
    }

    /**
     * 获取标记了 @DangerousOperation 的原始工具名集合。
     */
    public Set<String> getOriginallyDangerousToolNames() {
        return Set.copyOf(originallyDangerousToolNames);
    }

    /**
     * 危险操作工具回调装饰器
     * <p>
     * 只修改 LLM 看到的工具描述（追加确认提示），不拦截实际调用。
     * 确认流程由 AbstractExternalMcpTools 的 executeDangerous() 方法处理。
     */
    private static class DangerousOperationToolCallback implements ToolCallback {

        /** DESTRUCTIVE/PRIVILEGED 级别追加的确认提示（READ/REVERSIBLE 不追加，减少确认疲劳） */
        private static final String CONFIRMATION_SUFFIX =
                " 请直接调用此工具，系统会自动处理用户确认流程，不要自行用文字向用户确认。";

        private final ToolCallback delegate;
        private final ToolDefinition enhancedDefinition;

        DangerousOperationToolCallback(ToolCallback delegate, RiskLevel level) {
            this.delegate = delegate;
            ToolDefinition original = delegate.getToolDefinition();
            // L6 分级：仅不可逆/特权级追加确认提示；可逆/只读级直接执行
            String description = original.description();
            if (level == RiskLevel.DESTRUCTIVE || level == RiskLevel.PRIVILEGED) {
                description = description + CONFIRMATION_SUFFIX;
            }
            this.enhancedDefinition = DefaultToolDefinition.builder()
                    .name(original.name())
                    .description(description)
                    .inputSchema(original.inputSchema())
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return enhancedDefinition;
        }

        @Override
        public String call(String toolInput) {
            return delegate.call(toolInput);
        }

        @Override
        public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
            return delegate.call(toolInput, toolContext);
        }
    }

    /**
     * 动态描述工具回调装饰器。
     * <p>
     * 使用数据库中的覆盖配置替换原始的 description/inputSchema/dangerous 标记。
     * 如果覆盖配置中标记为危险操作，还会追加确认提示后缀。
     */
    private static class DynamicDescriptionToolCallback implements ToolCallback {

        private static final String CONFIRMATION_SUFFIX =
                " 请直接调用此工具，系统会自动处理用户确认流程，不要自行用文字向用户确认。";

        private final ToolCallback delegate;
        private final ToolDefinition overriddenDefinition;

        DynamicDescriptionToolCallback(ToolCallback delegate, ToolOverrideProvider.ToolOverride override) {
            this.delegate = delegate;
            ToolDefinition original = delegate.getToolDefinition();

            String description = override.description();
            if (override.dangerous()) {
                description += CONFIRMATION_SUFFIX;
            }

            this.overriddenDefinition = DefaultToolDefinition.builder()
                    .name(original.name())
                    .description(description)
                    .inputSchema(override.inputSchema() != null ? override.inputSchema() : original.inputSchema())
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return overriddenDefinition;
        }

        @Override
        public String call(String toolInput) {
            return delegate.call(toolInput);
        }

        @Override
        public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
            return delegate.call(toolInput, toolContext);
        }
    }
}
