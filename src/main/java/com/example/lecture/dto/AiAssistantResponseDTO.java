package com.example.lecture.dto;

import com.example.lecture.entity.Lecture;
import java.util.List;

public class AiAssistantResponseDTO {
    private String reply;
    private List<Lecture> lectures;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<Lecture> getLectures() {
        return lectures;
    }

    public void setLectures(List<Lecture> lectures) {
        this.lectures = lectures;
    }
}