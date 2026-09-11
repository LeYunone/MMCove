package com.mmcove.agent.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文生图响应 DTO（兼容 Go 项目出参）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FluxImageResponse {

    private Integer code;
    private Boolean success;
    private List<ImageData> data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageData {
        /** 图片 URL */
        private String url;
    }

    public static FluxImageResponse success(String url) {
        return FluxImageResponse.builder()
                .code(200)
                .success(true)
                .data(List.of(ImageData.builder().url(url).build()))
                .build();
    }

    public static FluxImageResponse error(Integer code, String message) {
        return FluxImageResponse.builder()
                .code(code)
                .success(false)
                .data(null)
                .build();
    }
}
