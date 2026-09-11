package com.mmcove.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmcove.agent.common.model.dto.FluxImageRequest;
import com.mmcove.agent.common.model.dto.FluxImageResponse;
import com.mmcove.agent.common.model.entity.AiChannel;
import com.mmcove.agent.common.model.entity.AiRole;
import com.mmcove.agent.infra.persistence.repository.AiRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * 文生图/图生图服务：通过渠道路由调用 Images Generations API。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FluxImageService {

    private final AiRoleRepository aiRoleRepository;
    private final ObjectMapper objectMapper;
    private final ChannelLoadBalancer channelLoadBalancer;
    private final DynamicChatModelFactory dynamicChatModelFactory;

    @Value("${mmcove.ai.dashboard.flux.file-save-path:/tmp/flux-images}")
    private String fileSavePath;

    @Value("${mmcove.ai.dashboard.flux.file-http-url:http://localhost:8808/flux-images}")
    private String fileHttpUrl;

    @Value("${mmcove.ai.dashboard.flux.default-size:1024x1024}")
    private String defaultSize;

    @Value("${mmcove.ai.dashboard.flux.image-analysis-role:def_image_analyzer}")
    private String imageAnalysisRoleCode;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    /**
     * 预处理图片分析（只做一次，在重试循环之前调用）。
     *
     * @param request   图片生成请求
     * @param groupName 分组名称
     * @return 构建好的 prompt，null 表示无需额外处理
     */
    public String preparePrompt(FluxImageRequest request, String groupName) {
        String prompt = request.getPrompt();

        if (request.getInput_image() != null && !request.getInput_image().isEmpty()) {
            log.info("[FluxImage] 检测到输入图片，开始意图识别");
            String analysisResult = analyzeImage(request.getInput_image(), groupName);
            if (analysisResult != null && !analysisResult.isEmpty()) {
                AiRole painterRole = aiRoleRepository.findByCode("def_painter").orElse(null);
                String stylePrompt = painterRole != null ? painterRole.getContent() : "";
                prompt = stylePrompt + " " + analysisResult;
                log.info("[FluxImage] 意图识别成功: result={}", analysisResult);
            } else {
                log.info("[FluxImage] 意图识别失败，回退到图生图模式");
            }
        }

        if (prompt == null || prompt.isEmpty()) {
            AiRole painterRole = aiRoleRepository.findByCode("def_painter").orElse(null);
            prompt = painterRole != null ? painterRole.getContent() : "";
        }

        return prompt;
    }

    /**
     * 调用 Flux API 生成图片（重试时只调用此方法，不重复分析）。
     *
     * @param request 图片生成请求
     * @param channel 渠道路由选中的渠道
     * @param prompt  预处理后的 prompt（来自 preparePrompt）
     */
    public FluxImageResponse generateImage(FluxImageRequest request, AiChannel channel, String prompt) {
        try {

            String size = request.getSize() != null ? request.getSize() : defaultSize;
            int n = request.getN() != null ? request.getN() : 1;
            String outputFormat = request.getOutput_format() != null ? request.getOutput_format() : "png";
            String model = request.getModel() != null ? request.getModel() : "flux.2-pro";

            // 2. 应用渠道的 modelMapping
            model = applyModelMapping(channel, model);

            // 3. 直接使用渠道配置的 baseUrl 作为完整 API 地址
            //    Azure FLUX 示例: https://xxx.services.ai.azure.com/providers/blackforestlabs/v1/flux-2-pro
            //    标准 OpenAI 示例: https://api.openai.com/v1/images/generations
            String url = channel.getBaseUrl().replaceAll("/+$", "");

            // 4. 构建请求体
            var bodyMap = new java.util.LinkedHashMap<String, Object>();
            bodyMap.put("prompt", prompt);
            bodyMap.put("n", n);
            bodyMap.put("size", size);
            bodyMap.put("output_format", outputFormat);
            bodyMap.put("model", model);
            if (request.getInput_image() != null && !request.getInput_image().isEmpty()) {
                bodyMap.put("input_image", request.getInput_image());
                log.info("[FluxImage] 图生图模式: inputImage length={}", request.getInput_image().length());
            }
            String requestBody = objectMapper.writeValueAsString(bodyMap);

            // 5. HTTP POST 调用，使用渠道的 apiKey
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + channel.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            log.info("[FluxImage] 调用 Images API: channelId={}, url={}, model={}", channel.getId(), url, model);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[FluxImage] API 调用失败: status={}, body={}", response.statusCode(), response.body());
                return FluxImageResponse.error(response.statusCode(), "Images API 调用失败: " + response.body());
            }

            // 6. 解析响应
            JsonNode jsonNode = objectMapper.readTree(response.body());
            JsonNode dataArray = jsonNode.get("data");
            if (dataArray == null || dataArray.isEmpty()) {
                log.error("[FluxImage] API 返回数据异常: body={}", response.body());
                return FluxImageResponse.error(500, "API 返回数据异常");
            }

            // 7. 处理返回数据（支持 b64_json 和 url 两种格式）
            String b64Json = null;
            String directUrl = null;
            JsonNode firstItem = dataArray.get(0);
            if (firstItem.has("b64_json")) {
                b64Json = firstItem.get("b64_json").asText();
            } else if (firstItem.has("url")) {
                directUrl = firstItem.get("url").asText();
            }

            String imageUrl;
            if (b64Json != null) {
                // base64 → 保存文件 → 返回 HTTP URL
                imageUrl = saveBase64Image(b64Json);
            } else if (directUrl != null) {
                imageUrl = directUrl;
            } else {
                log.error("[FluxImage] 返回数据中无 b64_json 或 url: body={}", response.body());
                return FluxImageResponse.error(500, "返回数据格式异常");
            }

            log.info("[FluxImage] 图片生成成功: imageUrl={}", imageUrl);
            return FluxImageResponse.success(imageUrl);

        } catch (Exception e) {
            log.error("[FluxImage] 图片生成失败: {}", e.getMessage(), e);
            return FluxImageResponse.error(500, "图片生成失败: " + e.getMessage());
        }
    }

    /**
     * 分析输入图片，识别其中的文字/图形意图。
     * 复用 ChannelLoadBalancer + DynamicChatModelFactory 进行渠道路由和模型调用。
     * 提示词从角色表加载（imageAnalysisRoleCode），支持动态管理。
     */
    private String analyzeImage(String inputImage, String groupName) {
        try {
            AiRole analysisRole = aiRoleRepository.findByCode(imageAnalysisRoleCode).orElse(null);
            if (analysisRole == null) {
                log.warn("[FluxImage] 图片分析角色不存在: code={}", imageAnalysisRoleCode);
                return null;
            }

            String analysisModel = analysisRole.getModel();
            if (analysisModel == null || analysisModel.isEmpty()) {
                log.warn("[FluxImage] 角色未配置模型: code={}", imageAnalysisRoleCode);
                return null;
            }

            ChannelLoadBalancer.SelectedChannel selected = channelLoadBalancer.selectChannel(
                    groupName != null ? groupName : "default", analysisModel, 0);
            if (selected == null || selected.channel() == null) {
                return null;
            }

            AiChannel analysisChannel = selected.channel();
            ChatModel chatModel = dynamicChatModelFactory.getOrCreate(analysisChannel);

            Media media = createImageMedia(inputImage);
            UserMessage.Builder userMsgBuilder = UserMessage.builder().media(media);
            if (analysisRole.getUserContent() != null && !analysisRole.getUserContent().isEmpty()) {
                userMsgBuilder.text(analysisRole.getUserContent());
            }
            UserMessage userMessage = userMsgBuilder.build();

            List<Message> messages;
            if (analysisRole.getContent() != null && !analysisRole.getContent().isEmpty()) {
                messages = List.of(new SystemMessage(analysisRole.getContent()), userMessage);
            } else {
                messages = List.of(userMessage);
            }

            Prompt prompt = buildAnalysisPrompt(messages, analysisChannel, selected.model());

            ChatResponse response = chatModel.call(prompt);
            String result = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText() : null;
            log.info("[FluxImage] 图片分析完成: role={}, result={}", imageAnalysisRoleCode, result);
            return result;
        } catch (Exception e) {
            log.error("[FluxImage] 图片分析失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建图片分析 Prompt，根据渠道类型设置模型选项。
     */
    private Prompt buildAnalysisPrompt(List<Message> messages, AiChannel channel, String modelName) {
        if (modelName == null) {
            return new Prompt(messages);
        }
        if (channel.getType() == AiChannel.TYPE_OPENAI) {
            return new Prompt(messages, OpenAiChatOptions.builder().model(modelName).build());
        } else if (channel.getType() == AiChannel.TYPE_ANTHROPIC) {
            return new Prompt(messages, AnthropicChatOptions.builder().model(modelName).build());
        }
        return new Prompt(messages);
    }

    /**
     * 根据输入格式创建 Media 对象。支持 raw base64、data URI 和 HTTP URL。
     */
    private Media createImageMedia(String inputImage) throws Exception {
        if (inputImage.startsWith("data:")) {
            int semicolon = inputImage.indexOf(';');
            int comma = inputImage.indexOf(',');
            MimeType mimeType = MimeType.valueOf(inputImage.substring(5, semicolon));
            String base64Data = inputImage.substring(comma + 1);
            byte[] bytes = Base64.getDecoder().decode(base64Data);
            return new Media(mimeType, new ByteArrayResource(bytes));
        }
        if (inputImage.startsWith("http://") || inputImage.startsWith("https://")) {
            return new Media(MimeTypeUtils.IMAGE_PNG, URI.create(inputImage));
        }
        // raw base64
        byte[] bytes = Base64.getDecoder().decode(inputImage);
        return new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(bytes));
    }

    /**
     * 应用渠道的模型名映射。
     */
    private String applyModelMapping(AiChannel channel, String model) {
        String mapping = channel.getModelMapping();
        if (mapping == null || mapping.isEmpty()) {
            return model;
        }
        try {
            var mappingMap = new ObjectMapper().readValue(mapping,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
            return mappingMap.getOrDefault(model, model);
        } catch (Exception e) {
            log.warn("[FluxImage] 解析模型映射失败: {}", e.getMessage());
            return model;
        }
    }

    /**
     * Base64 解码保存为文件，返回 HTTP URL。
     */
    private String saveBase64Image(String b64Json) throws IOException {
        byte[] imageBytes = Base64.getDecoder().decode(b64Json);
        String fileName = computeMd5(b64Json) + ".png";

        Path saveDir = Paths.get(fileSavePath);
        if (!Files.exists(saveDir)) {
            Files.createDirectories(saveDir);
        }
        Path filePath = saveDir.resolve(fileName);
        Files.write(filePath, imageBytes);

        return fileHttpUrl + "/" + fileName;
    }

    /**
     * 计算 MD5 哈希。
     */
    private String computeMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }
}
