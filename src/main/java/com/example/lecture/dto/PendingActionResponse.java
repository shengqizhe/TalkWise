package com.example.lecture.dto;

import com.example.lecture.entity.Lecture;
import lombok.Data;

@Data
public class PendingActionResponse {
    private Long actionId;
    private String type;
    private String status;
    private Lecture lecture;
    private Long resultId;

    public static PendingActionResponse of(Long actionId, String type, String status, Lecture lecture, Long resultId) {
        PendingActionResponse response = new PendingActionResponse();
        response.actionId = actionId;
        response.type = type;
        response.status = status;
        response.lecture = lecture;
        response.resultId = resultId;
        return response;
    }
}
