package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommonResponse<T> {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 数据
     */
    private T data;
    
    /**
     * 时间戳
     */
    private long timestamp;
    
    public static <T> CommonResponse<T> ok(T data) {
        return CommonResponse.<T>builder()
            .success(true)
            .message("success")
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    public static <T> CommonResponse<T> ok(String message, T data) {
        return CommonResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    public static <T> CommonResponse<T> error(String message) {
        return CommonResponse.<T>builder()
            .success(false)
            .message(message)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}
