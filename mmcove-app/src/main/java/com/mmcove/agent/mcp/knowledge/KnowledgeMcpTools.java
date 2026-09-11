package com.mmcove.agent.mcp.knowledge;

import com.mmcove.agent.common.context.KnowledgeContextHolder;
import com.mmcove.agent.rag.retrieve.KnowledgeRetrieveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * 知识库检索工具(挂 common 组,所有 Agent 自动可见)。
 *
 * <p>知识库由系统在第一次意图识别期自动选中(见 KnowledgeBaseRouter → KnowledgeContextHolder),
 * 无需用户/LLM 指定。本工具读上下文 kbId → 检索 → 返回相关切片供 LLM 组织回答。
 *
 * <p>kbId 经 {@code ObservableToolCallback} 的 snapshot/restore 桥接跨虚拟线程传递,
 * 否则 ReAct 工具回调线程读不到。
 *
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeMcpTools {

    private final KnowledgeRetrieveService knowledgeRetrieveService;

    @Tool(description = "检索知识库(产品手册、操作指南、业务规则、FAQ 等)。"
            + "当用户询问业务知识、操作流程、产品说明、规则、文档内容等问题时调用。"
            + "知识库已由系统根据问题场景自动选定,无需指定知识库名称。")
    public String searchKnowledge(
            @ToolParam(description = "检索查询:用户问题的关键词或核心诉求")
            String query) {

        Long kbId = KnowledgeContextHolder.get();
        if (kbId == null) {
            log.info("[searchKnowledge] 当前无选中知识库(未关联或未命中场景),跳过检索");
            return "当前场景未关联知识库,无法检索业务知识。";
        }
        log.info("[searchKnowledge] 检索: kbId={}, query={}", kbId, query);
        String context = knowledgeRetrieveService.retrieve(kbId, query);
        if (context == null || context.isBlank()) {
            return "知识库中未找到与该问题相关的内容。";
        }
        return "以下是知识库检索到的相关内容,请据此回答用户:\n\n" + context;
    }
}
