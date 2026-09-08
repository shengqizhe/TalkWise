package com.example.lecture.common.api;

import lombok.Getter;

/**
 * 统一返回结果状态码
 */
@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(404, "参数检验失败"),
    UNAUTHORIZED(401, "暂未登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限"),
    
    // 用户相关错误码
    USER_NOT_EXIST(1001, "用户不存在"),
    USERNAME_OR_PASSWORD_ERROR(1002, "用户名或密码错误"),
    DUPLICATE_USERNAME(1003, "用户名重复"),
    DUPLICATE_EMAIL(1004, "邮箱已被注册"),
    EMAIL_NOT_REGISTERED(1005, "该邮箱未注册"),
    DUPLICATE_PHONE(1006, "手机号已被注册"),
    
    // 系别相关错误码
    DEPARTMENT_NOT_EXIST(1101, "系别不存在"),
    DUPLICATE_DEPARTMENT_NAME(1102, "系别名称重复"),
    
    // 地点相关错误码
    LOCATION_NOT_EXIST(1201, "地点不存在"),
    DUPLICATE_LOCATION_NAME(1202, "地点名称重复"),
    LOCATION_IN_USE(1203, "地点正在被使用，无法删除"),
    
    // 讲座相关错误码
    LECTURE_NOT_EXIST(2001, "讲座不存在"),
    LECTURE_ALREADY_FULL(2002, "讲座已满"),
    LECTURE_ALREADY_ENDED(2003, "讲座已结束"),
    LECTURE_ALREADY_CANCELLED(2004, "讲座已取消"),
    
    // 报名相关错误码
    ALREADY_REGISTERED(3001, "已经报名过该讲座"),
    NOT_REGISTERED(3002, "未报名该讲座"),
    
    // 签到相关错误码
    ALREADY_CHECKED_IN(4001, "已经签到"),
    CHECK_IN_TIME_EXPIRED(4002, "签到时间已过"),
    
    // 互动相关错误码
    INTERACTION_NOT_EXIST(5001, "互动记录不存在"),
    
    // 评价相关错误码
    ALREADY_EVALUATED(6001, "已经评价过该讲座");
    
    private final int code;
    private final String message;
    
    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}