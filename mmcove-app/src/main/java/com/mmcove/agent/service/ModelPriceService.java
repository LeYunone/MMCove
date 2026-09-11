package com.mmcove.agent.service;

import com.mmcove.agent.common.model.entity.ModelPriceConfig;
import com.mmcove.agent.infra.persistence.repository.ModelPriceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 模型价格服务。
 * 查询模型计费配置，计算 Token 消耗。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelPriceService {

    private final ModelPriceConfigRepository modelPriceConfigRepository;

    /** 默认输入倍率 */
    private static final double DEFAULT_INPUT_RATIO = 1.0;
    /** 默认输出倍率 */
    private static final double DEFAULT_OUTPUT_RATIO = 1.0;

    /**
     * 查询模型价格配置。
     */
    public Optional<ModelPriceConfig> getPriceConfig(String modelName) {
        return modelPriceConfigRepository.findByModelName(modelName);
    }

    /**
     * 计算 Token 消耗费用。
     *
     * @param modelName    模型名称
     * @param inputTokens  输入 Token 数
     * @param outputTokens 输出 Token 数
     * @param groupRatio   分组倍率
     * @return 消耗的 Token 费用（整数）
     */
    public int calculateTokenCost(String modelName, int inputTokens, int outputTokens, double groupRatio) {
        Optional<ModelPriceConfig> configOpt = getPriceConfig(modelName);

        if (configOpt.isPresent()) {
            ModelPriceConfig config = configOpt.get();

            if (config.isFixedPrice()) {
                // 固定价格模式
                return config.getFixedPrice() != null ? config.getFixedPrice() : 0;
            }

            // 倍率模式
            double inputRatio = config.getInputRatio() != null ? config.getInputRatio() : DEFAULT_INPUT_RATIO;
            double outputRatio = config.getOutputRatio() != null ? config.getOutputRatio() : DEFAULT_OUTPUT_RATIO;
            double cost = (inputTokens * inputRatio + outputTokens * outputRatio) * groupRatio;
            return (int) Math.ceil(cost);
        }

        // 无配置时，默认 1:1 倍率
        double cost = (inputTokens * DEFAULT_INPUT_RATIO + outputTokens * DEFAULT_OUTPUT_RATIO) * groupRatio;
        return (int) Math.ceil(cost);
    }

    /**
     * 预估本次调用消耗的 Token 数（用于预扣费）。
     */
    public int estimatePreQuota(String modelName) {
        Optional<ModelPriceConfig> configOpt = getPriceConfig(modelName);
        if (configOpt.isPresent() && configOpt.get().isFixedPrice()) {
            return configOpt.get().getFixedPrice() != null ? configOpt.get().getFixedPrice() : 0;
        }
        // 预估 500 Token（保守估计）
        return 500;
    }
}
