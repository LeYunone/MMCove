package com.mmcove.agent.common.model.dto;

import lombok.Data;

/**
 * 文生图/图生图请求 DTO（兼容 Go 项目入参）。
 */
@Data
public class FluxImageRequest {

    /** 模型名称（默认 flux.2-pro） */
    private String model;

    /** 提示词 */
    private String prompt;

    /** 图生图：输入图片（base64 或 URL） */
    private String input_image;

    /** 图片尺寸（默认 1024x1024） */
    private String size;

    /** 生成数量（默认 1） */
    private Integer n;

    /** 输出格式（默认 png） */
    private String output_format;
}
