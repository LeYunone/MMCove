package com.mmcove.agent.mcp.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmcove.agent.common.context.TokenAuthContext;
import com.mmcove.agent.common.model.dto.FluxImageRequest;
import com.mmcove.agent.common.model.dto.FluxImageResponse;
import com.mmcove.agent.common.model.entity.AiChannel;
import com.mmcove.agent.service.ChannelLoadBalancer;
import com.mmcove.agent.service.FluxImageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 对话内生图 MCP 工具。
 *
 * <p>当 AI 在对话中识别到"画/生成/创作图片"意图时,通过 ReAct 自动调用本工具。
 * 内部复用 {@link FluxImageService} + {@link ChannelLoadBalancer} 渠道路由 + 失败重试,
 * 与 {@code DashboardAiController#flux2Pro} 同一套链路。
 *
 * <p>成功后返回带 {@code _imageEvent:true} 标记的 JSON,
 * 由 {@code ObservableToolCallback} 拦截并通过 SSE {@code image} 事件推送给前端。
 *
 * <p>由 {@code ToolRegistry}(BeanPostProcessor)自动扫描 @Tool 方法注册为 ToolCallback。
 * 需要在 {@code group_tool_config} 表给目标分组配置启用 {@code generateImage} 工具名,
 * {@code AgentOrchestrator#resolveGroupToolNames} 才会加载它。
 *
 * <p><b>分组获取</b>:通过 {@link TokenAuthContext#getGroupName()} 获取当前分组。
 * 该值由 {@code ObservableToolCallback} 在工具调用线程注入(否则 reactor-netty 线程里
 * 普通 ThreadLocal 会丢失),因此本工具总能拿到请求方的真实分组。
 *
 * @since 2026-06-30
 */
@Service
@RequiredArgsConstructor
public class ImageGenerationTools {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationTools.class);

    /** 默认生图模型 */
    private static final String DEFAULT_MODEL = "flux.2-pro";

    /** 默认图片尺寸(与 FluxImageService.defaultSize 保持一致) */
    private static final String DEFAULT_SIZE = "1024x1024";

    /** 渠道重试上限(与 DashboardAiController.flux2Pro 一致) */
    private static final int MAX_RETRY = 3;

    private final FluxImageService fluxImageService;
    private final ChannelLoadBalancer channelLoadBalancer;
    private final ObjectMapper objectMapper;

    @Tool(description = "【生成图片】根据文字描述生成一张图片。"
            + "当用户要求「画一张/画一个/画一只/生成图片/创作图片/给我来一张图」等生图意图时调用本工具。"
            + "调用成功后,图片会自动显示在用户界面上,你只需用自然语言简要描述已生成的内容即可,"
            + "不要在回复里再使用 markdown 图片语法(![...](...))重复贴图。"
            + "size 支持 1024x1024/1024x1792/1792x1024 等,留空使用默认值。"
            + "model 一般留空即可(默认 flux.2-pro)。")
    public String generateImage(
            @ToolParam(description = "图片的文字描述(提示词),必填") String prompt,
            @ToolParam(description = "图片尺寸,如 1024x1024,可选") String size,
            @ToolParam(description = "生图模型名称,可选(默认 flux.2-pro)") String model) {
        if (isBlank(prompt)) {
            return err("prompt 不能为空");
        }

        // 从 TokenAuthContext 获取分组(ObservableToolCallback 已在工具线程注入)
        TokenAuthContext context = TokenAuthContext.get();
        String groupName = context != null ? context.getGroupName() : null;
        if (isBlank(groupName)) {
            groupName = "default";
        }

        String resolvedModel = notBlank(model) ? model.trim() : DEFAULT_MODEL;
        String resolvedSize = notBlank(size) ? size.trim() : DEFAULT_SIZE;
        String rawPrompt = prompt.trim();

        // 构造生图请求(对话场景默认纯文生图,不带 input_image)
        FluxImageRequest request = new FluxImageRequest();
        request.setPrompt(rawPrompt);
        request.setModel(resolvedModel);
        request.setSize(resolvedSize);

        // 1. 预处理 prompt(注入画师风格;图生图场景还会做意图识别,本工具不带 input_image 故仅注入风格)
        String preparedPrompt;
        try {
            preparedPrompt = fluxImageService.preparePrompt(request, groupName);
        } catch (Exception e) {
            log.error("[ImageGenerationTools] preparePrompt 失败: {}", e.getMessage(), e);
            return err("提示词预处理失败: " + e.getMessage());
        }

        // 2. 渠道路由 + 最多 3 次重试(与 flux2Pro 一致)
        Set<Long> triedChannelIds = new HashSet<>();
        for (int retry = 0; retry < MAX_RETRY; retry++) {
            ChannelLoadBalancer.SelectedChannel selected;
            try {
                selected = channelLoadBalancer.selectChannel(groupName, resolvedModel, retry, triedChannelIds);
            } catch (Exception e) {
                log.warn("[ImageGenerationTools] 渠道选择异常: group={}, model={}, retry={}, err={}",
                        groupName, resolvedModel, retry, e.getMessage());
                break;
            }
            if (selected == null || selected.channel() == null) {
                log.warn("[ImageGenerationTools] 无可用渠道: group={}, model={}, retry={}",
                        groupName, resolvedModel, retry);
                break;
            }

            AiChannel channel = selected.channel();
            triedChannelIds.add(channel.getId());
            log.info("[ImageGenerationTools] 使用渠道: channelId={}, name={}, retry={}",
                    channel.getId(), channel.getName(), retry);

            FluxImageResponse response;
            try {
                response = fluxImageService.generateImage(request, channel, preparedPrompt);
            } catch (Exception e) {
                log.warn("[ImageGenerationTools] 渠道调用异常: channelId={}, retry={}, err={}",
                        channel.getId(), retry, e.getMessage());
                continue;
            }

            if (Boolean.TRUE.equals(response.getSuccess())
                    && response.getData() != null && !response.getData().isEmpty()) {
                String imageUrl = response.getData().get(0).getUrl();
                log.info("[ImageGenerationTools] 图片生成成功: imageUrl={}, channelId={}",
                        imageUrl, channel.getId());
                return okWithImage(imageUrl, preparedPrompt, rawPrompt, resolvedSize, resolvedModel);
            }

            log.warn("[ImageGenerationTools] 渠道调用失败: channelId={}, code={}, retry={}/{}",
                    channel.getId(), response.getCode(), retry + 1, MAX_RETRY);
        }

        return err("所有图片生成渠道均不可用");
    }

    // ==================== 内部辅助 ====================

    private String okWithImage(String imageUrl, String prompt, String rawPrompt,
                                String size, String model) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        // 标记位:ObservableToolCallback 据此推送 image 事件给前端
        resp.put("_imageEvent", true);
        resp.put("imageUrl", imageUrl);
        resp.put("prompt", prompt);
        resp.put("rawPrompt", rawPrompt);
        resp.put("size", size);
        resp.put("model", model);
        return toJson(resp);
    }

    private String err(String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", false);
        // 失败时也带标记位(显式 false),ObservableToolCallback 据此跳过 image 事件
        resp.put("_imageEvent", false);
        resp.put("message", message);
        return toJson(resp);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"success\":false,\"_imageEvent\":false,\"message\":\"JSON 序列化失败:"
                    + e.getMessage() + "\"}";
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean notBlank(String s) {
        return !isBlank(s);
    }
}
