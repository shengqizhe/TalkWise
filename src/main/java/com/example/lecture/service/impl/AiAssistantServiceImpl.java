package com.example.lecture.service.impl;

import com.example.lecture.dto.AiAssistantRequestDTO;
import com.example.lecture.dto.AiAssistantResponseDTO;
import com.example.lecture.dto.RegistrationLectureDTO;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.Location;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.mapper.LocationMapper;
import com.example.lecture.service.AiAssistantService;
import com.example.lecture.service.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    @Autowired
    private LectureMapper lectureMapper;

    @Autowired
    private LocationMapper locationMapper;
    
    @Autowired
    private RegistrationService registrationService;

    private static final String QIANWEN_API_KEY = "REDACTED-KEY";
    private static final String QIANWEN_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    @Override
    public AiAssistantResponseDTO chat(AiAssistantRequestDTO request) {
        String context = request.getContext();
        String message = request.getMessage();
        // 关键词识别：如果是查报名，直接查数据库
        if (message != null && (
            message.contains("我的报名") ||
            message.contains("已报名") ||
            message.contains("报名记录")
    )) {
        Long userId = null;
        try {
            if (request.getUserId() != null) {
                userId = Long.valueOf(request.getUserId());
            }
        } catch (Exception e) {
            AiAssistantResponseDTO dto = new AiAssistantResponseDTO();
            dto.setReply("用户ID格式错误，无法查询报名信息。");
            return dto;
        }
        return getUserRegistrations(userId);
    }
        // 1. 分析意图，精准过滤讲座
        QueryIntent intent = analyzeQueryIntent(message);
        List<Lecture> filteredLectures = queryLecturesByIntent(intent);
        // 2. 拼接讲座信息（只取近期可报名讲座，避免prompt过长）
        List<Lecture> lectures = lectureMapper.selectAllPublished();
        StringBuilder lectureInfo = new StringBuilder();
        int maxLectures = 10; // 最多拼10场，防止prompt过长
        for (int i = 0; i < lectures.size() && i < maxLectures; i++) {
            Lecture lec = lectures.get(i);
            // 查询地点详细信息
            String locationStr = "未知地点";
            if (lec.getLocationId() != null) {
                Location loc = locationMapper.selectById(lec.getLocationId());
                if (loc != null) {
                    locationStr = loc.getName();
                    if (loc.getAddress() != null && !loc.getAddress().isEmpty()) {
                        locationStr += "（" + loc.getAddress() + "）";
                    }
                }
            }
            lectureInfo.append("讲座标题：").append(lec.getTitle()).append("\n")
                .append("主讲人：").append(lec.getSpeaker()).append("\n")
                .append("时间：").append(formatLectureTime(lec.getLectureTime())).append("\n")
                .append("地点：").append(locationStr).append("\n")
                .append("简介：").append(lec.getSummary()).append("\n\n");
        }
        // 3. 设计prompt
        String prompt = "你是大学讲座推荐智能助手。" +
                "以下是用户和AI的历史对话（如有）：\n" + (context == null ? "" : context) + "\n" +
                "当前用户提问：" + message + "\n" +
                "以下是近期可报名讲座信息：\n" + lectureInfo.toString() +
                "请根据用户需求、历史上下文和讲座信息，智能推荐最合适的讲座或给出合理答复。如果没有合适讲座，请礼貌说明。";
        // 4. 调用千问API
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + QIANWEN_API_KEY);
            Map<String, Object> body = new HashMap<>();
            body.put("model", "qwen-turbo");
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);
            body.put("messages", messages);
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<String>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(QIANWEN_API_URL, entity, String.class);
            String aiReply = "";
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode result = objectMapper.readTree(response.getBody());
                if (result.has("choices")) {
                    JsonNode choices = result.get("choices");
                    if (choices.isArray() && choices.size() > 0) {
                        JsonNode messageNode = choices.get(0).get("message");
                        if (messageNode != null && messageNode.has("content")) {
                            aiReply = messageNode.get("content").asText("").trim();
                        }
                    }
                }
            }
            AiAssistantResponseDTO dto = new AiAssistantResponseDTO();
            dto.setReply(aiReply);
            // 只返回相关讲座
            dto.setLectures(filteredLectures);
            return dto;
        } catch (Exception e) {
            e.printStackTrace();
            AiAssistantResponseDTO dto = new AiAssistantResponseDTO();
            dto.setReply("AI服务暂时不可用，请稍后再试。");
            dto.setLectures(new ArrayList<>());
            return dto;
        }
    }

    @Override
    public AiAssistantResponseDTO getUserRegistrations(Long userId) {
        try {
            List<RegistrationLectureDTO> registrations = registrationService.getUserRegistrations(userId, 1); // 1表示已报名状态
            AiAssistantResponseDTO dto = new AiAssistantResponseDTO();
            
            if (registrations.isEmpty()) {
                dto.setReply("您目前还没有报名任何讲座。我可以为您推荐一些精彩的讲座，或者您可以告诉我您的兴趣领域，我会为您找到合适的讲座。");
                dto.setLectures(new ArrayList<>());
            } else {
                StringBuilder reply = new StringBuilder("以下是您已报名的讲座：\n\n");
                List<Lecture> lectures = new ArrayList<>();
                
                for (RegistrationLectureDTO registration : registrations) {
                    Lecture lecture = lectureMapper.selectById(registration.getLectureId());
                    if (lecture != null) {
                        lectures.add(lecture);
                        reply.append("📅 ").append(lecture.getTitle()).append("\n");
                        reply.append("   主讲人：").append(lecture.getSpeaker()).append("\n");
                        reply.append("   时间：").append(formatLectureTime(lecture.getLectureTime())).append("\n");
                        reply.append("   报名时间：").append(registration.getRegisterTime()).append("\n\n");
                    }
                }
                
                dto.setReply(reply.toString());
                dto.setLectures(lectures);
            }
            
            return dto;
        } catch (Exception e) {
            e.printStackTrace();
            AiAssistantResponseDTO dto = new AiAssistantResponseDTO();
            dto.setReply("获取报名信息失败，请稍后再试。");
            dto.setLectures(new ArrayList<>());
            return dto;
        }
    }

    @Override
    public AiAssistantResponseDTO cancelUserRegistration(Long userId, Long lectureId) {
        try {
            // 获取讲座信息
            Lecture lecture = lectureMapper.selectById(lectureId);
            if (lecture == null) {
                AiAssistantResponseDTO dto = new AiAssistantResponseDTO();
                dto.setReply("讲座不存在，无法取消报名。");
                dto.setLectures(new ArrayList<>());
                return dto;
            }
            
            // 取消报名
            registrationService.cancel(userId, lectureId);
            
            AiAssistantResponseDTO dto = new AiAssistantResponseDTO();
            dto.setReply("已成功取消报名《" + lecture.getTitle() + "》。如果您需要重新报名或有其他需求，请告诉我。");
            dto.setLectures(new ArrayList<>());
            return dto;
        } catch (Exception e) {
            e.printStackTrace();
            AiAssistantResponseDTO dto = new AiAssistantResponseDTO();
            dto.setReply("取消报名失败：" + e.getMessage() + "。请稍后再试。");
            dto.setLectures(new ArrayList<>());
            return dto;
        }
    }

    /**
     * 分析查询意图
     */
    private QueryIntent analyzeQueryIntent(String message) {
        QueryIntent intent = new QueryIntent();
        
        // 检查是否是时间查询
        if (isTimeQuery(message)) {
            intent.setType(QueryType.TIME);
            intent.setTimeInfo(extractTimeInfo(message));
            return intent;
        }
        
        // 检查是否是讲师查询
        String speaker = extractSpeaker(message);
        if (speaker != null && !speaker.isEmpty()) {
            intent.setType(QueryType.SPEAKER);
            intent.setKeyword(speaker);
            return intent;
        }
        
        // 检查是否是关键词查询
        if (isKeywordQuery(message)) {
            intent.setType(QueryType.KEYWORD);
            intent.setKeyword(extractKeyword(message));
            return intent;
        }
        
        // 默认使用AI分析
        intent.setType(QueryType.AI_ANALYSIS);
        intent.setKeyword(extractKeywordFromAI(message));
        return intent;
    }

    /**
     * 判断是否是时间查询
     */
    private boolean isTimeQuery(String message) {
        String[] timePatterns = {
            "周[一二三四五六日天]",
            "星期[一二三四五六日天]",
            "周[一二三四五六日天].*[上午下午晚上]",
            "星期[一二三四五六日天].*[上午下午晚上]",
            "今天.*[上午下午晚上]",
            "明天.*[上午下午晚上]",
            "后天.*[上午下午晚上]",
            "这周.*[上午下午晚上]",
            "下周.*[上午下午晚上]"
        };
        
        for (String pattern : timePatterns) {
            if (Pattern.compile(pattern).matcher(message).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取时间信息
     */
    private TimeInfo extractTimeInfo(String message) {
        TimeInfo timeInfo = new TimeInfo();
        
        // 提取星期几
        Pattern dayPattern = Pattern.compile("周([一二三四五六日天])|星期([一二三四五六日天])");
        Matcher dayMatcher = dayPattern.matcher(message);
        if (dayMatcher.find()) {
            String day = dayMatcher.group(1) != null ? dayMatcher.group(1) : dayMatcher.group(2);
            timeInfo.setDayOfWeek(convertChineseDayToNumber(day));
        }
        
        // 提取时间段
        if (message.contains("上午")) {
            timeInfo.setTimeSlot("上午");
        } else if (message.contains("下午")) {
            timeInfo.setTimeSlot("下午");
        } else if (message.contains("晚上")) {
            timeInfo.setTimeSlot("晚上");
        }
        
        return timeInfo;
    }

    /**
     * 中文星期转换为数字
     */
    private int convertChineseDayToNumber(String chineseDay) {
        switch (chineseDay) {
            case "一": return 1;
            case "二": return 2;
            case "三": return 3;
            case "四": return 4;
            case "五": return 5;
            case "六": return 6;
            case "日":
            case "天": return 7;
            default: return 0;
        }
    }

    /**
     * 判断是否是关键词查询
     */
    private boolean isKeywordQuery(String message) {
        // 简单的关键词判断逻辑
        return message.length() <= 20 && !message.contains("?") && !message.contains("？");
    }

    /**
     * 提取关键词
     */
    private String extractKeyword(String message) {
        // 简单的关键词提取
        return message.trim();
    }

    /**
     * 使用AI提取关键词
     */
    private String extractKeywordFromAI(String message) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + QIANWEN_API_KEY);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "qwen-turbo");
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", "请从以下问题中提取1-3个关键词，只返回关键词，用逗号分隔：" + message);
            messages.add(userMsg);
            body.put("messages", messages);

            ObjectMapper objectMapper = new ObjectMapper();
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(QIANWEN_API_URL, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode result = objectMapper.readTree(response.getBody());
                if (result.has("choices")) {
                    JsonNode choices = result.get("choices");
                    if (choices.isArray() && choices.size() > 0) {
                        JsonNode messageNode = choices.get(0).get("message");
                        if (messageNode != null && messageNode.has("content")) {
                            String keyword = messageNode.get("content").asText("").trim();
                            // 取第一个关键词
                            if (keyword.contains(",")) {
                                keyword = keyword.split(",")[0].trim();
                            }
                            return keyword;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 新增讲师名提取方法
     */
    private String extractSpeaker(String message) {
        // 优先匹配"张飞老师"、"李教授"等，返回"张飞"、"李"
        Pattern pattern = Pattern.compile("([\u4e00-\u9fa5]{1,4})(教授|老师|讲师)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1); // 只返回人名部分
        }
        // 支持"主讲人是张飞老师"
        pattern = Pattern.compile("主讲人[是为]?([\u4e00-\u9fa5]{1,4})(教授|老师|讲师)");
        matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // 兜底：直接匹配2-4位中文人名（如"张飞"），但只在包含"讲师/老师/教授/主讲人"等关键词时生效
        if (message.contains("讲师") || message.contains("老师") || message.contains("教授") || message.contains("主讲人")) {
            pattern = Pattern.compile("([\u4e00-\u9fa5]{2,4})");
            matcher = pattern.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * 根据意图查询讲座
     */
    private List<Lecture> queryLecturesByIntent(QueryIntent intent) {
        switch (intent.getType()) {
            case TIME:
                return queryLecturesByTime(intent.getTimeInfo());
            case SPEAKER:
                return intent.getKeyword().isEmpty() ? List.of() : lectureMapper.selectBySpeaker(intent.getKeyword());
            case KEYWORD:
            case AI_ANALYSIS:
                return intent.getKeyword().isEmpty() ? List.of() : lectureMapper.selectByKeyword(intent.getKeyword());
            default:
                return List.of();
        }
    }

    /**
     * 根据时间查询讲座
     */
    private List<Lecture> queryLecturesByTime(TimeInfo timeInfo) {
        // 这里需要根据时间信息查询数据库
        // 由于数据库中的lecture_time是LocalDateTime，我们需要根据星期几和时间段来查询
        if (timeInfo.getDayOfWeek() > 0) {
            return lectureMapper.selectByDayOfWeek(timeInfo.getDayOfWeek(), timeInfo.getTimeSlot());
        }
        return List.of();
    }

    /**
     * 生成智能回复
     */
    private String generateSmartReply(QueryIntent intent, List<Lecture> lectures, String originalMessage) {
        StringBuilder reply = new StringBuilder();
        
        switch (intent.getType()) {
            case TIME:
                reply.append(generateTimeBasedReply(intent.getTimeInfo(), lectures));
                break;
            case KEYWORD:
            case AI_ANALYSIS:
                reply.append(generateKeywordBasedReply(intent.getKeyword(), lectures));
                break;
        }
        
        return reply.toString();
    }

    /**
     * 生成基于时间的回复
     */
    private String generateTimeBasedReply(TimeInfo timeInfo, List<Lecture> lectures) {
        StringBuilder reply = new StringBuilder();
        
        String dayName = getDayName(timeInfo.getDayOfWeek());
        String timeSlot = timeInfo.getTimeSlot();
        
        if (lectures.isEmpty()) {
            reply.append("很抱歉，").append(dayName).append(timeSlot).append("暂时没有安排讲座。\n");
            reply.append("建议你可以：\n");
            reply.append("1. 查看其他时间段的讲座安排\n");
            reply.append("2. 关注后续的讲座更新\n");
            reply.append("3. 或者告诉我你对什么主题感兴趣，我可以为你推荐相关讲座");
        } else {
            reply.append("太好了！").append(dayName).append(timeSlot).append("有以下讲座安排：\n\n");
            for (int i = 0; i < lectures.size(); i++) {
                Lecture lecture = lectures.get(i);
                reply.append(i + 1).append(". 《").append(lecture.getTitle()).append("》\n");
                reply.append("   主讲人：").append(lecture.getSpeaker()).append("\n");
                reply.append("   时间：").append(formatLectureTime(lecture.getLectureTime())).append("\n");
                reply.append("   地点：").append(lecture.getLocationId()).append("\n");
                reply.append("   简介：").append(lecture.getSummary()).append("\n\n");
            }
            reply.append("这些讲座看起来都很精彩！你可以选择感兴趣的进行报名。");
        }
        
        return reply.toString();
    }

    /**
     * 生成基于关键词的回复
     */
    private String generateKeywordBasedReply(String keyword, List<Lecture> lectures) {
        StringBuilder reply = new StringBuilder();
        
        if (lectures.isEmpty()) {
            reply.append("关于【").append(keyword).append("】的讲座，目前暂时没有找到相关安排。\n");
            reply.append("不过你可以：\n");
            reply.append("1. 尝试其他相关关键词\n");
            reply.append("2. 查看所有讲座列表，也许会有你感兴趣的内容\n");
            reply.append("3. 告诉我具体的时间段，我可以帮你查看那个时间有什么讲座");
        } else {
            reply.append("找到了").append(lectures.size()).append("个关于【").append(keyword).append("】的讲座：\n\n");
            for (int i = 0; i < lectures.size(); i++) {
                Lecture lecture = lectures.get(i);
                reply.append(i + 1).append(". 《").append(lecture.getTitle()).append("》\n");
                reply.append("   主讲人：").append(lecture.getSpeaker()).append("\n");
                reply.append("   时间：").append(formatLectureTime(lecture.getLectureTime())).append("\n");
                reply.append("   地点：").append(lecture.getLocationId()).append("\n");
                reply.append("   简介：").append(lecture.getSummary()).append("\n\n");
            }
            reply.append("这些讲座都很不错！你可以选择合适的时间参加。");
        }
        
        return reply.toString();
    }

    /**
     * 获取星期名称
     */
    private String getDayName(int dayOfWeek) {
        switch (dayOfWeek) {
            case 1: return "周一";
            case 2: return "周二";
            case 3: return "周三";
            case 4: return "周四";
            case 5: return "周五";
            case 6: return "周六";
            case 7: return "周日";
            default: return "";
        }
    }

    /**
     * 格式化讲座时间
     */
    private String formatLectureTime(LocalDateTime lectureTime) {
        if (lectureTime == null) return "时间待定";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM月dd日 HH:mm");
        return lectureTime.format(formatter);
    }

    // 内部类定义
    private static class QueryIntent {
        private QueryType type;
        private String keyword;
        private TimeInfo timeInfo;

        public QueryType getType() { return type; }
        public void setType(QueryType type) { this.type = type; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public TimeInfo getTimeInfo() { return timeInfo; }
        public void setTimeInfo(TimeInfo timeInfo) { this.timeInfo = timeInfo; }
    }

    private static class TimeInfo {
        private int dayOfWeek;
        private String timeSlot;

        public int getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
        public String getTimeSlot() { return timeSlot; }
        public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    }

    private enum QueryType {
        TIME, KEYWORD, AI_ANALYSIS, SPEAKER
    }
} 