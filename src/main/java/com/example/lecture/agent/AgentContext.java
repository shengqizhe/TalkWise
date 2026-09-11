package com.example.lecture.agent;

/**
 * Agent 执行上下文：当前请求的用户身份透传给工具方法。
 * 工具不应信任模型传入的 userId，只能从这里取当前登录用户，防止越权。
 */
public final class AgentContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<java.util.Set<String>> ROLES = new ThreadLocal<>();

    private AgentContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /** 可能为 null（未登录游客），工具需自行处理 */
    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setRoles(java.util.Set<String> roles) {
        ROLES.set(roles == null ? java.util.Set.of() : roles);
    }

    /** 当前用户角色（小写）集合；未设置时为空集 */
    public static java.util.Set<String> getRoles() {
        java.util.Set<String> roles = ROLES.get();
        return roles == null ? java.util.Set.of() : roles;
    }

    public static void clear() {
        USER_ID.remove();
        ROLES.remove();
    }
}
