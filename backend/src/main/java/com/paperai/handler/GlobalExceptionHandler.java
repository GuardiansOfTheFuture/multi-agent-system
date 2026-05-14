package com.paperai.handler;

import com.paperai.common.BusinessException;
import com.paperai.common.ResultCode;
import com.paperai.model.vo.ApiResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全局异常处理器
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResultVO<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResultVO.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResultVO<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return ApiResultVO.error(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    /**
     * AI 服务异常 — Spring AI / DashScope 调用失败
     * 该类异常的 message 可能是原始的 HTTP 400 JSON，需要解析为友好提示
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResultVO<Void> handleRuntimeException(RuntimeException e) {
        String friendlyMsg = extractFriendlyMessage(e);
        log.error("运行时异常: {}", friendlyMsg, e);
        return ApiResultVO.error(ResultCode.INTERNAL_ERROR.getCode(), friendlyMsg);
    }

    /**
     * 兜底异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResultVO<Void> handleException(Exception e) {
        String friendlyMsg = extractFriendlyMessage(e);
        log.error("系统异常: {}", friendlyMsg, e);
        return ApiResultVO.error(ResultCode.INTERNAL_ERROR.getCode(), friendlyMsg);
    }

    // ===== 辅助方法 =====

    /** DashScope 400 错误 JSON 的正则 */
    private static final Pattern DASHSCOPE_ERROR_PATTERN = Pattern.compile(
            "\"code\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"message\"\\s*:\\s*\"([^\"]+)\""
    );

    /** HTTP 状态码行前缀模式 */
    private static final Pattern HTTP_ERROR_LINE = Pattern.compile(
            "^\\d{3}\\s*-?\\s*"
    );

    /**
     * 从异常链中提取对用户友好的中文错误信息
     */
    private String extractFriendlyMessage(Throwable e) {
        // 遍历异常链
        Throwable current = e;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null) {
                // 尝试解析 DashScope JSON 格式的 error
                String parsed = parseDashScopeError(msg);
                if (parsed != null) {
                    return parsed;
                }
                // 去掉 HTTP 状态码前缀（如 "400 - "）
                String cleaned = cleanHttpPrefix(msg);
                if (!cleaned.equals(msg) && cleaned.length() > 3) {
                    return cleaned;
                }
            }
            current = current.getCause();
        }
        // 兜底
        String original = e.getMessage();
        if (original != null && original.length() > 0) {
            return "AI 服务返回异常：" + truncate(original, 200);
        }
        return "AI 服务暂不可用，请稍后重试";
    }

    /**
     * 尝试将 DashScope 的错误 JSON 转为中文友好提示
     */
    private String parseDashScopeError(String raw) {
        if (raw == null) return null;
        Matcher m = DASHSCOPE_ERROR_PATTERN.matcher(raw);
        if (m.find()) {
            String code = m.group(1);
            String message = m.group(2);
            return "AI 服务错误 [" + code + "]：" + message;
        }
        return null;
    }

    /**
     * 去掉 "400 - " 或 "500 - " 这样的 HTTP 状态码前缀
     */
    private String cleanHttpPrefix(String raw) {
        if (raw == null) return raw;
        return HTTP_ERROR_LINE.matcher(raw).replaceFirst("").trim();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
