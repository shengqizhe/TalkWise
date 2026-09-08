# 动态提醒时间配置指南

## 概述

讲座管理系统现在支持根据讲座开始时间动态计算提醒时间，而不是使用固定的提醒时间。这样可以为不同时间段的讲座提供更合适的提醒策略。

## 配置说明

### 启用动态提醒

在 `application.yml` 或 `application-reminder.yml` 中配置：

```yaml
lecture:
  reminder:
    # 启用动态提醒时间计算
    dynamic-reminder-enabled: true
    
    # 动态提醒规则
    dynamic-reminder-rules: "morning:30,afternoon:60,evening:90,night:120"
    
    # 最小和最大提醒时间限制
    min-reminder-minutes: 15
    max-reminder-minutes: 180
```

### 时间段定义

- **morning（上午）**: 6:00 - 12:00
- **afternoon（下午）**: 12:00 - 18:00  
- **evening（晚上）**: 18:00 - 24:00
- **night（深夜）**: 0:00 - 6:00

### 提醒规则示例

| 讲座开始时间 | 时间段 | 提醒时间 | 说明 |
|-------------|--------|----------|------|
| 09:00 | morning | 30分钟前 | 上午讲座，提前30分钟提醒 |
| 14:00 | afternoon | 60分钟前 | 下午讲座，提前1小时提醒 |
| 19:30 | evening | 90分钟前 | 晚上讲座，提前1.5小时提醒 |
| 02:00 | night | 120分钟前 | 深夜讲座，提前2小时提醒 |

## 配置参数详解

### dynamic-reminder-enabled
- **类型**: boolean
- **默认值**: false
- **说明**: 是否启用动态提醒时间计算

### dynamic-reminder-rules
- **类型**: String
- **格式**: "时间段:分钟数,时间段:分钟数"
- **示例**: "morning:30,afternoon:60,evening:90,night:120"
- **说明**: 定义不同时间段的提醒时间

### min-reminder-minutes
- **类型**: int
- **默认值**: 15
- **说明**: 最小提醒时间，防止提醒时间过短

### max-reminder-minutes
- **类型**: int
- **默认值**: 180
- **说明**: 最大提醒时间，防止提醒时间过长

## 使用场景

### 1. 工作日配置
适合工作日的讲座安排：
```yaml
dynamic-reminder-rules: "morning:45,afternoon:60,evening:90"
```

### 2. 周末配置
适合周末的讲座安排：
```yaml
dynamic-reminder-rules: "morning:60,afternoon:90,evening:120"
```

### 3. 学术会议配置
适合学术会议的密集安排：
```yaml
dynamic-reminder-rules: "morning:30,afternoon:30,evening:45"
min-reminder-minutes: 15
max-reminder-minutes: 60
```

## 回退机制

当动态提醒功能关闭时（`dynamic-reminder-enabled: false`），系统会使用固定的 `reminder-minutes` 配置。

## 日志监控

启用详细日志可以监控动态提醒的执行情况：
```yaml
verbose-logging: true
```

日志示例：
```
📧 开始发送邮件提醒 - 讲座: Java高级编程, 总报名数: 25, 有效报名数: 23, 提醒时间: 60分钟
✅ 邮件发送成功 - 用户: 张三 (zhang@example.com), 提醒时间: 60分钟
🎯 即将开始通知发送完成 - 讲座: Java高级编程, 邮件发送成功: 23, 邮件发送失败: 0, 总处理数: 23, 提醒时间: 60分钟
```

## 注意事项

1. 动态提醒规则的时间段必须覆盖24小时
2. 提醒时间会受到 `min-reminder-minutes` 和 `max-reminder-minutes` 的限制
3. 如果某个时间段没有配置规则，会使用默认的 `reminder-minutes`
4. 修改配置后需要重启应用才能生效