package com.mmcove.agent.mcp.external;

import com.mmcove.agent.common.context.TokenAuthContext;
import com.mmcove.agent.common.model.entity.KnowledgeBase;
import com.mmcove.agent.common.model.entity.ProductLine;
import com.mmcove.agent.infra.persistence.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库产品线隔离策略(唯一实现点,外部 MCP 工具经此访问)。
 *
 * <p>身份来源(按优先级):
 * <ol>
 *   <li><b>默认产品线</b>:MCP 客户端配置的 {@code X-Product-Line} 请求头(各自配置各自的产品线名,
 *       见 {@code McpProductLineResolver} 解析),由拦截器经会话身份桥注入 TokenAuthContext;</li>
 *   <li>token 绑定的产品线(api_token.product_line_id,未配 header 时兜底)。</li>
 * </ol>
 *
 * <p>行为规则(用户拍板):
 * <ul>
 *   <li><b>读</b>(检索/目录):默认只搜默认产品线的库 + 全部共享库;话术带产品线名称时,
 *       「指定线 + 默认线」两线库 + 共享库<b>合并查询</b>;</li>
 *   <li><b>写</b>(上传):目标库属于默认线直接执行;属于话术指定线时先询问
 *       (抛 {@link NeedConfirmException},用户确认后带 confirm=true 重调);其余拒绝。</li>
 * </ul>
 *
 * @since 2026-09-07
 */
@Service
@RequiredArgsConstructor
public class KbAccessService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final McpProductLineResolver productLineResolver;

    /** 当前默认产品线(未配置 = null,只能读共享库) */
    public Long currentProductLineId() {
        return TokenAuthContext.get().getProductLineId();
    }

    /**
     * 解析话术中的产品线名称(可空)。
     *
     * @throws IllegalArgumentException 名称不存在或歧义
     */
    public ProductLine resolveNamedLine(String productLineName) {
        return StringUtils.hasText(productLineName) ? productLineResolver.resolve(productLineName) : null;
    }

    /** 查询范围产品线集合:指定线 + 默认线(去重;两者皆空返回空表=仅共享库) */
    public List<Long> scopeLineIds(Long namedLineId) {
        Long defaultLineId = currentProductLineId();
        Map<Long, Long> ids = new LinkedHashMap<>();
        if (namedLineId != null) {
            ids.put(namedLineId, namedLineId);
        }
        if (defaultLineId != null) {
            ids.put(defaultLineId, defaultLineId);
        }
        return new ArrayList<>(ids.keySet());
    }

    /**
     * 当前可读的知识库集合:指定线(可空) + 默认线 + 全部共享库。
     *
     * @throws IllegalArgumentException 指定名称不存在或歧义
     */
    public List<KnowledgeBase> readableKnowledgeBases(String productLineName) {
        ProductLine named = resolveNamedLine(productLineName);
        return knowledgeBaseRepository.findEnabledByProductLinesIncludeShared(
                scopeLineIds(named == null ? null : named.getId()));
    }

    /**
     * 校验知识库可读并返回实体(属于指定线/默认线之一,或共享库)。
     *
     * @throws IllegalArgumentException 不可读
     */
    public KnowledgeBase requireReadable(Long kbId, String productLineName) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(kbId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + kbId));
        ProductLine named = resolveNamedLine(productLineName);
        boolean inNamed = named != null && named.getId().equals(kb.getProductLineId());
        boolean inDefault = kb.getProductLineId() != null
                && kb.getProductLineId().equals(currentProductLineId());
        if (!inNamed && !inDefault && !Boolean.TRUE.equals(kb.getIsShared())) {
            throw new IllegalArgumentException("无权访问知识库 [" + kb.getName()
                    + "]:产品线隔离(若需读取其他产品线,请在问题中带上产品线名称)");
        }
        return kb;
    }

    /**
     * 校验知识库可写并返回实体。
     * <ul>
     *   <li>库属于默认产品线 → 直接通过;</li>
     *   <li>库属于话术指定产品线(非默认线) → 抛 {@link NeedConfirmException} 要求用户确认
     *       (confirm=true 时通过)——"上传的时候进行询问";</li>
     *   <li>其余(共享库非归属线/其他线) → 拒绝。</li>
     * </ul>
     */
    public KnowledgeBase requireWritable(Long kbId, String productLineName, boolean confirmed) {
        Long defaultLineId = currentProductLineId();
        if (defaultLineId == null) {
            throw new IllegalArgumentException("当前 MCP 未配置默认产品线(X-Product-Line 请求头),无法写入知识库");
        }
        KnowledgeBase kb = knowledgeBaseRepository.findById(kbId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + kbId));
        Long lineId = kb.getProductLineId();
        if (lineId == null) {
            throw new IllegalArgumentException("知识库 [" + kb.getName() + "] 未归属产品线,无法写入");
        }
        if (lineId.equals(defaultLineId)) {
            return kb;
        }
        ProductLine named = resolveNamedLine(productLineName);
        if (named != null && lineId.equals(named.getId())) {
            if (!confirmed) {
                throw new NeedConfirmException("目标知识库 [" + kb.getName() + "] 属于产品线 ["
                        + named.getName() + "](非默认产品线),请向用户确认是否上传到该产品线;"
                        + "用户确认后携带 confirm=yes 重新调用本工具");
            }
            return kb;
        }
        throw new IllegalArgumentException("知识库 [" + kb.getName() + "] 不在可写范围"
                + "(默认产品线" + (named != null ? "或指定产品线 " + named.getName() : "") + "),无法写入");
    }
}
