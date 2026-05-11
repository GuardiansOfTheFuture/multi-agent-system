package com.paperai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应结果
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResultVO<T> {

    private int code;
    private String message;
    private T data;

    public static <T> ApiResultVO<T> success(T data) {
        return new ApiResultVO<>(200, "success", data);
    }

    public static <T> ApiResultVO<T> success(String message, T data) {
        return new ApiResultVO<>(200, message, data);
    }

    public static <T> ApiResultVO<T> error(int code, String message) {
        return new ApiResultVO<>(code, message, null);
    }

    public static <T> ApiResultVO<T> error(String message) {
        return new ApiResultVO<>(500, message, null);
    }
}
