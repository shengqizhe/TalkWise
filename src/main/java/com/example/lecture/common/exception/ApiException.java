package com.example.lecture.common.exception;

import com.example.lecture.common.api.ResultCode;
import lombok.Getter;

/**
 * 自定义API异常
 */
@Getter
public class ApiException extends RuntimeException {
    
    private final ResultCode resultCode;
    
    public ApiException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }
    
    public ApiException(String message) {
        super(message);
        this.resultCode = ResultCode.FAILED;
    }
    
    public ApiException(Throwable cause) {
        super(cause);
        this.resultCode = ResultCode.FAILED;
    }
    
    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.resultCode = ResultCode.FAILED;
    }
} 