package com.mmcove.agent.llm.advisor;

import com.mmcove.agent.common.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 租户上下文拦截器，将租户信息传递到 LLM 调用中。
 */
@Slf4j
@Component
public class TenantContextAdvisor implements CallAdvisor {

    private static final String TENANT_ID_KEY = "tenantId";
    private static final String USER_ID_KEY = "userId";

    @Override
    public String getName() {
        return "TenantContextAdvisor";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        if (tenantId != null || userId != null) {
            Map<String, Object> context = new HashMap<>(request.context());
            if (tenantId != null) {
                context.put(TENANT_ID_KEY, tenantId);
            }
            if (userId != null) {
                context.put(USER_ID_KEY, userId);
            }
            request = request.mutate().context(context).build();
        }

        return chain.nextCall(request);
    }
}
