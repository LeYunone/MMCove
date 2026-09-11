package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型计费配置实体，对应 model_price_config 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_price_config")
public class ModelPriceConfig extends BaseEntity<Long> {

    /** 模型名称 */
    private String modelName;

    /** 输入倍率(相对基础价格) */
    private Double inputRatio;

    /** 输出倍率(相对基础价格) */
    private Double outputRatio;

    /** 是否使用固定价格: 0=倍率, 1=固定 */
    private Integer useFixedPrice;

    /** 固定价格(Token数/次) */
    private Integer fixedPrice;

    /** 是否启用 */
    private Integer enabled;

    public boolean isFixedPrice() {
        return useFixedPrice != null && useFixedPrice == 1;
    }

    public boolean isEnabled() {
        return enabled != null && enabled == 1;
    }
}
