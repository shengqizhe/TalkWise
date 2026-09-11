package com.example.lecture.agent.web;

import cn.dev33.satoken.stp.StpUtil;
import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentEngine;
import com.example.lecture.agent.dto.AgentChatRequest;
import com.example.lecture.agent.dto.AgentChatResponse;
import com.example.lecture.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 对话接口（完整路径 /api/agent/chat，context-path 为 /api）
 */
@Tag(name = "Agent", description = "Agent 智能助手（工具调用 + 多轮决策）")
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private final AgentEngine agentEngine;

    @Operation(summary = "Agent 对话")
    @PostMapping("/chat")
    public Result<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        String message = request.getMessage();
        if (message == null || message.isBlank()) {
            return Result.failed("消息不能为空");
        }
        // 用户身份取自登录态；未登录游客也可咨询讲座类问题
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        AgentContext.setUserId(userId);
        try {
            AgentEngine.ChatResult result = agentEngine.chat(userId, message.trim());
            AgentChatResponse response = new AgentChatResponse();
            response.setReply(result.reply());
            response.setTools(result.tools());
            return Result.success(response);
        } finally {
            AgentContext.clear();
        }
    }
}
