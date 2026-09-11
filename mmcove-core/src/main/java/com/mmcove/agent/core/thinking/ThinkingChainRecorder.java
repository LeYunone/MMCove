package com.mmcove.agent.core.thinking;

import com.mmcove.agent.common.model.entity.ThinkingTurn;
import com.mmcove.agent.infra.persistence.repository.ThinkingTurnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 思维链记录器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThinkingChainRecorder {

    private final ThinkingTurnRepository thinkingTurnRepository;

    /**
     * 记录一个思考轮次。
     */
    public ThinkingTurn record(String sessionId, String subTaskId,
                               int turnIndex, String thought,
                               String actionTool, String actionArgs,
                               String observation) {
        ThinkingTurn turn = new ThinkingTurn();
        turn.setSessionId(sessionId);
        turn.setSubTaskId(subTaskId);
        turn.setTurnIndex(turnIndex);
        turn.setThought(thought);
        turn.setActionTool(actionTool);
        turn.setActionArgs(actionArgs);
        turn.setObservation(observation);
        turn.setStatus("COMPLETED");

        try {
            return thinkingTurnRepository.save(turn);
        } catch (Exception e) {
            log.warn("思维链记录失败: {}", e.getMessage());
            return turn;
        }
    }
}
