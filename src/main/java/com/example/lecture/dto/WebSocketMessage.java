package com.example.lecture.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket消息实体类
 */
@Data
public class WebSocketMessage {
    
    /**
     * 消息类型
     */
    private String type;
    
    /**
     * 发送者ID
     */
    private Long senderId;
    
    /**
     * 接收者ID
     */
    private Long receiverId;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 消息时间
     */
    private Long timestamp = System.currentTimeMillis();
    
    /**
     * 消息状态(0:未读 1:已读)
     */
    private Integer status = 0;
    
    /**
     * 附加数据
     */
    private Map<String, Object> data = new HashMap<>();
    
    /**
     * 添加附加数据
     * @param key 键
     * @param value 值
     */
    public void addData(String key, Object value) {
        if (data == null) {
            data = new HashMap<>();
        }
        data.put(key, value);
    }
} 