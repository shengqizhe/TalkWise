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
    /** 取消原因（仅取消类动作） */
    private String reason;
    /** 面向用户的动作描述，前端确认卡直接展示 */
    private String summary;

    public static PendingActionResponse of(Long actionId, String type, String status, Lecture lecture, Long resultId) {
        return of(actionId, type, status, lecture, resultId, null);
    }

    public static PendingActionResponse of(Long actionId, String type, String status, Lecture lecture,
                                           Long resultId, String reason) {
        PendingActionResponse response = new PendingActionResponse();
        response.actionId = actionId;
        response.type = type;
        response.status = status;
        response.lecture = lecture;
        response.resultId = resultId;
        response.reason = reason;
        response.summary = describe(type, lecture, reason);
        return response;
    }

    /** 生成人类可读的动作摘要，供确认卡展示 */
    private static String describe(String type, Lecture lecture, String reason) {
        if (type == null) {
            return "";
        }
        String title = lecture == null || lecture.getTitle() == null ? "该讲座" : "《" + lecture.getTitle() + "》";
        return switch (type) {
            case "CREATE_LECTURE" -> "创建讲座 " + title;
            case "UPDATE_LECTURE" -> "修改讲座 " + title;
            case "CANCEL_LECTURE" -> "取消讲座 " + title + (reason == null || reason.isBlank() ? "" : "（原因：" + reason + "）");
            case "PUBLISH_LECTURE" -> (lecture != null && lecture.getPublishStatus() != null && lecture.getPublishStatus() == 1
                    ? "发布讲座 " : "下架讲座 ") + title;
            default -> title;
        };
    }
}
