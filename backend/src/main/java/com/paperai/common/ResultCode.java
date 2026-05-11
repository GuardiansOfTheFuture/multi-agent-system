package com.paperai.common;

/**
 * 统一返回码
 *
 * @author: ch
 * @date 2026年05月11日
 */
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    CREATED(201, "创建成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    // 业务异常
    PAPER_NOT_FOUND(1001, "论文不存在"),
    TASK_NOT_FOUND(1002, "任务不存在"),
    AGENT_EXECUTION_ERROR(2001, "Agent 执行异常"),
    AI_SERVICE_ERROR(2002, "AI 服务调用失败"),
    INVALID_INPUT(3001, "输入参数校验失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
