package com.example.lecture.agent;

/**
 * 模型调用故障（供应商报错、余额不足、限流、超时、网络异常、返回格式非法等）。
 *
 * <p>对用户统一展示 {@link #USER_MESSAGE}，真实原因只进日志，供管理员排查；
 * 避免把供应商报文或 API Key 状态直接暴露给终端用户。
 */
public class LlmUnavailableException extends RuntimeException {

    /** 面向用户的统一提示文案 */
    public static final String USER_MESSAGE = "抱歉！功能失效，请联系管理员修复。";

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 判断异常链是否源于模型调用故障（供应商报错、余额/限流、超时、网络异常等）。
     * 用于决定给用户展示统一文案还是通用失败提示。
     */
    public static boolean isModelFailure(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof LlmUnavailableException) {
                return true;
            }
            String name = cur.getClass().getName();
            if (name.startsWith("dev.langchain4j") || name.startsWith("dev.ai4j")) {
                return true;
            }
            if (cur instanceof java.io.IOException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
