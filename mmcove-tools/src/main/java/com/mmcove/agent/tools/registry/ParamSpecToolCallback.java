package com.mmcove.agent.tools.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmcove.agent.tools.external.annotation.ParamSpec;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 参数规约校验装饰器:在工具真正执行前,按方法参数上的 {@link ParamSpec} 校验 + 归一 LLM 传入的参数。
 *
 * <p>处理规则:
 * <ul>
 *   <li>非法值不透传,直接返回 {@code {success:false,message:"合法值..."}}——
 *       LLM 看到提示会自动重填(self-healing),工具不会被错误参数调用</li>
 *   <li>合法但为别名(如「中文」)则归一为标准值(如 zh_CN)后重写 toolInput 透传</li>
 *   <li>无 {@code @ParamSpec} 的参数零影响(透传),装饰器对未标注工具完全无副作用</li>
 * </ul>
 *
 * <p>「精准防飘移」L5:与 {@code DangerousOperationToolCallback} 同级,由 {@link ToolRegistry} 统一组装,
 * 一处覆盖所有 MCP 工具。新增工具只需在参数标 {@code @ParamSpec} 即获得防护。
 *
 * @since 2026-07-21
 */
public class ParamSpecToolCallback implements ToolCallback {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolCallback delegate;
    private final Method method;

    public ParamSpecToolCallback(ToolCallback delegate, Method method) {
        this.delegate = delegate;
        this.method = method;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // 1. 解析 toolInput(JSON)。非 JSON 则不拦,交给原工具处理。
        Map<String, Object> args;
        try {
            args = MAPPER.readValue(toolInput, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return callDelegate(toolInput, toolContext);
        }

        // 2. 逐参数按 @ParamSpec 校验 + 归一
        boolean changed = false;
        for (Parameter param : method.getParameters()) {
            ParamSpec spec = resolveSpec(param);
            if (spec == null) {
                continue;
            }
            String paramName = param.getName();
            Object rawVal = args.get(paramName);
            String rawStr = (rawVal == null) ? null : String.valueOf(rawVal).trim();

            // required 校验
            if (spec.required() && (rawStr == null || rawStr.isEmpty())) {
                return err("参数「" + paramName + "」必填,请向用户询问后提供");
            }
            if (rawStr == null || rawStr.isEmpty()) {
                continue;
            }

            // enum 归一(命中别名 → 标准值;不命中 → err 带 legalHint 触发自愈)
            if (spec.enumValues().length > 0) {
                String normalized = EnumNormalizer.normalize(rawStr, spec.enumValues());
                if (normalized == null) {
                    return err("参数「" + paramName + "」值「" + rawStr + "」无法识别,合法值:"
                            + EnumNormalizer.legalHint(spec.enumValues()));
                }
                if (!normalized.equals(rawStr)) {
                    args.put(paramName, normalized);
                    changed = true;
                }
            }

            // 数值范围
            boolean hasRange = spec.min() != Double.NEGATIVE_INFINITY
                    || spec.max() != Double.POSITIVE_INFINITY;
            if (hasRange) {
                try {
                    double d = Double.parseDouble(rawStr);
                    if (d < spec.min() || d > spec.max()) {
                        return err("参数「" + paramName + "」值「" + rawStr + "」越界,合法范围 "
                                + num(spec.min()) + " ~ " + num(spec.max()));
                    }
                } catch (NumberFormatException e) {
                    return err("参数「" + paramName + "」应为数值,实际「" + rawStr + "」");
                }
            }

            // 正则
            if (!spec.regex().isEmpty() && !rawStr.matches(spec.regex())) {
                return err("参数「" + paramName + "」值「" + rawStr + "」格式不符(要求:" + spec.regex() + ")");
            }
        }

        // 3. 有归一改动则重写 toolInput,否则原样透传
        String finalInput = changed ? toJson(args) : toolInput;
        return callDelegate(finalInput, toolContext);
    }

    /** 取参数上的 @ParamSpec(支持 @Repeatable 单个/容器两种形态)。 */
    private ParamSpec resolveSpec(Parameter param) {
        ParamSpec direct = param.getAnnotation(ParamSpec.class);
        if (direct != null) {
            return direct;
        }
        ParamSpec.Container container = param.getAnnotation(ParamSpec.Container.class);
        if (container != null && container.value().length > 0) {
            return container.value()[0];
        }
        return null;
    }

    private String callDelegate(String input, ToolContext ctx) {
        return (ctx != null) ? delegate.call(input, ctx) : delegate.call(input);
    }

    private String err(String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", false);
        r.put("_paramSpecError", true);
        r.put("message", message);
        return toJson(r);
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"参数校验失败\"}";
        }
    }

    /** 数值展示:整数去 .0(让 err 提示更友好,如「0 ~ 100」而非「0.0 ~ 100.0」)。 */
    private String num(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }
}
