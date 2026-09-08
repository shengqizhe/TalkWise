package com.example.lecture.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表：启动时扫描所有 Spring Bean 中带 @AgentTool 的方法，
 * 提供 工具查找 / 参数 JSON 反序列化 / 反射执行。
 */
@Slf4j
@Component
public class AgentToolRegistry implements ApplicationContextAware {

    private ApplicationContext applicationContext;
    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 一个已注册工具的定义（注解元数据 + 执行目标） */
    public static class ToolDefinition {
        public String name;
        public String description;
        public ToolType type;
        public Object bean;
        public Method method;
        public List<ParamMeta> params = new ArrayList<>();

        public static class ParamMeta {
            public String name;
            public String description;
            public boolean required;
            public Class<?> javaType;
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void scan() {
        int count = 0;
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue;
            }
            if (bean == null) {
                continue;
            }
            Class<?> clazz = ClassUtils.getUserClass(bean.getClass());
            for (Method method : clazz.getMethods()) {
                AgentTool agentTool = AnnotationUtils.findAnnotation(method, AgentTool.class);
                if (agentTool == null) {
                    continue;
                }
                ToolDefinition def = new ToolDefinition();
                def.name = agentTool.name();
                def.description = agentTool.description();
                def.type = agentTool.type();
                def.bean = bean;
                // 从目标类取真实方法（getMethods 返回的可能是接口方法，需按名匹配实现类方法）
                try {
                    def.method = clazz.getMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    def.method = method;
                }
                for (Parameter p : def.method.getParameters()) {
                    AgentParam ap = p.getAnnotation(AgentParam.class);
                    if (ap == null) {
                        continue;
                    }
                    ToolDefinition.ParamMeta meta = new ToolDefinition.ParamMeta();
                    meta.name = ap.name();
                    meta.description = ap.description();
                    meta.required = ap.required();
                    meta.javaType = p.getType();
                    def.params.add(meta);
                }
                tools.put(def.name, def);
                count++;
            }
        }
        log.info("Agent 工具注册完成，共 {} 个：{}", count, tools.keySet());
    }

    public ToolDefinition find(String name) {
        return tools.get(name);
    }

    public List<ToolDefinition> allTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 执行工具：把模型给的 JSON arguments 按 @AgentParam 声明注入方法参数。
     */
    public String execute(ToolDefinition def, String argumentsJson) {
        try {
            Map<String, Object> args = new LinkedHashMap<>();
            if (argumentsJson != null && !argumentsJson.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(argumentsJson, Map.class);
                args.putAll(parsed);
            }
            Object[] params = new Object[def.params.size()];
            for (int i = 0; i < def.params.size(); i++) {
                ToolDefinition.ParamMeta meta = def.params.get(i);
                Object raw = args.get(meta.name);
                if (raw == null && meta.required) {
                    return "参数缺失：" + meta.name;
                }
                params[i] = convert(raw, meta.javaType);
            }
            def.method.setAccessible(true);
            Object result = def.method.invoke(def.bean, params);
            return result == null ? "（无返回）" : String.valueOf(result);
        } catch (Exception e) {
            log.error("工具执行失败: {}", def.name, e);
            return "工具执行失败：" + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }
    }

    private Object convert(Object raw, Class<?> type) {
        if (raw == null) {
            return null;
        }
        if (type == String.class) {
            return String.valueOf(raw);
        }
        if (type == Long.class || type == long.class) {
            return raw instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(raw));
        }
        if (type == Integer.class || type == int.class) {
            return raw instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(raw));
        }
        if (type == Boolean.class || type == boolean.class) {
            return raw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(raw));
        }
        return raw;
    }
}
