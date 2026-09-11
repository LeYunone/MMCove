package com.mmcove.agent.config;

import com.mmcove.agent.common.crypto.BcryptUtil;
import com.mmcove.agent.common.model.entity.AiChannel;
import com.mmcove.agent.common.model.entity.ApiUser;
import com.mmcove.agent.infra.persistence.repository.AiChannelRepository;
import com.mmcove.agent.infra.persistence.repository.ApiUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动数据初始化。
 * 将 yml 中配置的 Spring AI ChatModel 注册为系统默认渠道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AiChannelRepository aiChannelRepository;
    private final ApiUserRepository apiUserRepository;
    private final Map<String, ChatModel> chatModels;

    @Override
    public void run(String... args) {
        log.info("[DataInitializer] 开始初始化数据...");
        seedAdmin();
        registerSystemChannels();
        log.info("[DataInitializer] 数据初始化完成");
    }

    /**
     * 初始化默认超管账号：admin/admin123（全新库）+ mmcove/mmcove123（非冲突兜底，
     * 确保即便 admin 已被旧数据占用也能用 BCrypt 管理员登录后台）。已存在则跳过。
     */
    private void seedAdmin() {
        seedOne("admin", "admin123");
        seedOne("mmcove", "mmcove123");
    }

    private void seedOne(String username, String password) {
        try {
            if (apiUserRepository.findByUsername(username).isPresent()) {
                return;
            }
            ApiUser u = new ApiUser();
            u.setUsername(username);
            u.setPassword(BcryptUtil.hashText(password));
            u.setRole(ApiUser.ROLE_ROOT_USER);
            u.setStatus(ApiUser.STATUS_ENABLED);
            u.setQuota(0L);
            apiUserRepository.insert(u);
            log.info("[DataInitializer] 已创建超管: {} / {}", username, password);
        } catch (Exception e) {
            log.warn("[DataInitializer] 创建超管 {} 失败: {}", username, e.getMessage());
        }
    }

    /**
     * 将 Spring 容器中的 ChatModel 注册为系统默认渠道。
     */
    private void registerSystemChannels() {
        // 检查是否已有系统渠道
        List<AiChannel> existing = aiChannelRepository.findSystemChannels();
        if (!existing.isEmpty()) {
            log.info("[DataInitializer] 已存在 {} 个系统渠道，跳过注册", existing.size());
            return;
        }

        // 注册 OpenAI 系统渠道
        ChatModel openaiModel = chatModels.get("openAiChatModel");
        if (openaiModel != null) {
            registerSystemChannel("系统默认 OpenAI 渠道", AiChannel.TYPE_OPENAI, openaiModel);
        }

        // 注册 Anthropic 系统渠道
        ChatModel anthropicModel = chatModels.get("anthropicChatModel");
        if (anthropicModel != null) {
            registerSystemChannel("系统默认 Anthropic 渠道", AiChannel.TYPE_ANTHROPIC, anthropicModel);
        }
    }

    /**
     * 注册单个系统渠道。
     */
    private void registerSystemChannel(String name, int type, ChatModel chatModel) {
        try {
            AiChannel channel = new AiChannel();
            channel.setName(name);
            channel.setType(type);
            channel.setStatus(1);
            channel.setIsSystem(1);
            channel.setWeight(10);
            channel.setPriority(0L);
            channel.setAutoBan(0);
            channel.setGroupName("default");
            channel.setUsedQuota(0L);

            // 尝试从 ChatModel 中提取 apiKey 和 baseUrl
            extractApiConfig(channel, chatModel);

            aiChannelRepository.insert(channel);
            log.info("[DataInitializer] 注册系统渠道: name={}, type={}, id={}",
                    name, type, channel.getId());
        } catch (Exception e) {
            log.warn("[DataInitializer] 注册系统渠道失败: name={}, error={}", name, e.getMessage());
        }
    }

    /**
     * 填充默认渠道的 API 配置。
     * Spring AI 2.0 模型内部使用官方 SDK 客户端，不便反射；直接从环境变量读取默认凭证。
     */
    private void extractApiConfig(AiChannel channel, ChatModel chatModel) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String baseUrl = System.getenv("OPENAI_BASE_URL");
        if (apiKey != null) {
            channel.setApiKey(apiKey);
        }
        if (baseUrl != null) {
            channel.setBaseUrl(baseUrl);
        }
    }
}
