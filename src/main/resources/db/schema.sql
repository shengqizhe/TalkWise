-- 创建数据库
CREATE DATABASE IF NOT EXISTS university_lecture_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE university_lecture_management;

-- 系别表
CREATE TABLE IF NOT EXISTS `department` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '系别ID',
    `department_name` VARCHAR(100) NOT NULL COMMENT '系别名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '系别描述',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_department_name` (`department_name`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系别表';


-- 地点表
CREATE TABLE IF NOT EXISTS `location` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '地点ID',
    `name` VARCHAR(200) NOT NULL COMMENT '地点名称',
    `longitude` DECIMAL(10,7) NOT NULL COMMENT '经度',
    `latitude` DECIMAL(10,7) NOT NULL COMMENT '纬度',
    `type` VARCHAR(50) NOT NULL COMMENT '地点类型（如building）',
    `address` VARCHAR(200) DEFAULT NULL COMMENT '详细地址（可选）',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_location_name` (`name`) COMMENT '地点名称唯一索引'
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地点表';
-- 用户相关表
-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码',
    `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
    `student_teacher_id` VARCHAR(50) NOT NULL COMMENT '学号/教师编号',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `department_id` BIGINT DEFAULT NULL COMMENT '系别ID',
    `interest_tags` JSON DEFAULT NULL COMMENT '兴趣标签(JSON格式)',
    `participation_score` DECIMAL(3,1) DEFAULT 0.0 COMMENT '参与度评分',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_student_teacher_id` (`student_teacher_id`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`),
    CONSTRAINT `fk_user_department_id` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `role_description` VARCHAR(200) NOT NULL COMMENT '角色描述',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_name` (`role_name`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 权限表
CREATE TABLE IF NOT EXISTS `permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    `permission_code` VARCHAR(50) NOT NULL COMMENT '权限编码',
    `permission_name` VARCHAR(100) NOT NULL COMMENT '权限名称',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`permission_code`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
     PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    CONSTRAINT `fk_user_role_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_user_role_role_id` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS `role_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    CONSTRAINT `fk_role_permission_role_id` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`),
    CONSTRAINT `fk_role_permission_permission_id` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 讲座管理表
-- 讲座类别表
CREATE TABLE IF NOT EXISTS `lecture_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '类别ID',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父类别ID',
    `category_name` VARCHAR(100) NOT NULL COMMENT '类别名称',
    `level` TINYINT NOT NULL COMMENT '级别(1:学科大类 2:研究方向 3:主题)',
    `heat_score` DECIMAL(3,1) DEFAULT 0.0 COMMENT '热度评分',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_lecture_category_parent_id` FOREIGN KEY (`parent_id`) REFERENCES `lecture_category` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='讲座类别表';

-- 讲座表
CREATE TABLE IF NOT EXISTS `lecture` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '讲座ID',
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `summary` TEXT NOT NULL COMMENT '摘要',
    `content` TEXT NOT NULL COMMENT '内容',
    `seo_title` VARCHAR(200) DEFAULT NULL COMMENT 'SEO标题(AI生成)',
    `keywords` VARCHAR(200) DEFAULT NULL COMMENT '关键词(AI生成)',
    `category_id` BIGINT NOT NULL COMMENT '类别ID',
    `host_department_id` BIGINT DEFAULT NULL COMMENT '主办系别ID',
    `speaker` VARCHAR(100) NOT NULL COMMENT '主讲人',
    `location_id` BIGINT DEFAULT NULL COMMENT '关联的地点ID',
    `lecture_time` DATETIME NOT NULL COMMENT '讲座时间',
    `capacity` INT NOT NULL COMMENT '容量',
    `registered_count` INT DEFAULT 0 COMMENT '报名人数',
    `organizer_id` BIGINT NOT NULL COMMENT '组织者ID',
    `promotion_content` VARCHAR(255) DEFAULT NULL COMMENT '讲座宣传内容(图片)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1:未开始 2:进行中 3:已结束 4:已取消)',
    `publish_status` TINYINT NOT NULL DEFAULT 0 COMMENT '发布状态(0:未发布 1:已发布)',
    `recommend_score` DECIMAL(3,1) DEFAULT 0.0 COMMENT '推荐分数',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_lecture_category_id` FOREIGN KEY (`category_id`) REFERENCES `lecture_category` (`id`),
    CONSTRAINT `fk_lecture_organizer_id` FOREIGN KEY (`organizer_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_lecture_host_department_id` FOREIGN KEY (`host_department_id`) REFERENCES `department` (`id`),
    CONSTRAINT `fk_lecture_location_id` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='讲座表';

-- 报名与签到表
-- 报名记录表
CREATE TABLE IF NOT EXISTS `registration` (
     `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '报名ID',
     `user_id` BIGINT NOT NULL COMMENT '用户ID',
     `lecture_id` BIGINT NOT NULL COMMENT '讲座ID',
     `register_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
     `cancel_time` DATETIME DEFAULT NULL COMMENT '取消时间',
     `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1:已报名 2:已取消)',
     `checkin_status` TINYINT NOT NULL DEFAULT 0 COMMENT '签到状态(0:未签到 1:已签到)',
     `checkin_time` DATETIME DEFAULT NULL COMMENT '签到时间',
     `recommend_reason` VARCHAR(500) DEFAULT NULL COMMENT '推荐理由(AI生成)',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_lecture` (`user_id`, `lecture_id`),
    CONSTRAINT `fk_registration_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_registration_lecture_id` FOREIGN KEY (`lecture_id`) REFERENCES `lecture` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名记录表';

-- 签到表
CREATE TABLE IF NOT EXISTS `attendance` (
   `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '签到ID',
   `user_id` BIGINT NOT NULL COMMENT '用户ID',
   `lecture_id` BIGINT NOT NULL COMMENT '讲座ID',
   `check_in_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签到时间',
   `check_in_type` TINYINT NOT NULL COMMENT '签到方式(1:二维码 2:人脸识别)',
   `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1:正常 2:迟到 3:早退)',
   `face_recognition_score` DECIMAL(3,1) DEFAULT NULL COMMENT '人脸识别分数',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_lecture` (`user_id`, `lecture_id`),
    CONSTRAINT `fk_attendance_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_attendance_lecture_id` FOREIGN KEY (`lecture_id`) REFERENCES `lecture` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到表';

-- 互动与反馈表
-- 互动表
CREATE TABLE IF NOT EXISTS `interaction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '互动ID',
    `lecture_id` BIGINT NOT NULL COMMENT '讲座ID',
    `question_user_id` BIGINT NOT NULL COMMENT '提问用户ID',
    `answer_user_id` BIGINT DEFAULT NULL COMMENT '回答用户ID',
    `content` TEXT NOT NULL COMMENT '内容',
    `is_question` TINYINT NOT NULL DEFAULT 1 COMMENT '是否为提问(1:是 0:否)',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_interaction_lecture_id` FOREIGN KEY (`lecture_id`) REFERENCES `lecture` (`id`),
    CONSTRAINT `fk_interaction_question_user_id` FOREIGN KEY (`question_user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_interaction_answer_user_id` FOREIGN KEY (`answer_user_id`) REFERENCES `user` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='互动表';

-- 评价表
CREATE TABLE IF NOT EXISTS `evaluation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `lecture_id` BIGINT NOT NULL COMMENT '讲座ID',
    `score` DECIMAL(3,1) NOT NULL COMMENT '评分',
    `content` TEXT NOT NULL COMMENT '评价内容',
    `sentiment_score` DECIMAL(3,1) DEFAULT NULL COMMENT '情感极性分数',
    `improvement_suggestion` TEXT DEFAULT NULL COMMENT '改进建议(AI生成)',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_lecture` (`user_id`, `lecture_id`),
    CONSTRAINT `fk_evaluation_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_evaluation_lecture_id` FOREIGN KEY (`lecture_id`) REFERENCES `lecture` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';

-- 通知提醒表
-- 消息表
CREATE TABLE IF NOT EXISTS `message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `type` TINYINT NOT NULL COMMENT '消息类型(1:系统通知 2:讲座通知 3:互动通知)',
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `content` TEXT NOT NULL COMMENT '内容',
    `sender_id` BIGINT NOT NULL COMMENT '发送者ID',
    `channel` TINYINT NOT NULL COMMENT '发送渠道(1:站内信 2:邮件 3:短信)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '发送状态(1:待发送 2:已发送 3:发送失败)',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_message_sender_id` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- 消息用户关联表
CREATE TABLE IF NOT EXISTS `message_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `message_id` BIGINT NOT NULL COMMENT '消息ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `read_status` TINYINT NOT NULL DEFAULT 0 COMMENT '阅读状态(0:未读 1:已读)',
    `read_time` DATETIME DEFAULT NULL COMMENT '阅读时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_user` (`message_id`, `user_id`),
    CONSTRAINT `fk_message_user_message_id` FOREIGN KEY (`message_id`) REFERENCES `message` (`id`),
    CONSTRAINT `fk_message_user_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息用户关联表';

-- AI专项表
-- 兴趣标签表
CREATE TABLE IF NOT EXISTS `interest_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `tag_name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `type` TINYINT NOT NULL COMMENT '类型(1:系统生成 2:自定义)',
    `confidence_score` DECIMAL(3,1) DEFAULT 0.0 COMMENT '置信度分数',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_name` (`tag_name`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='兴趣标签表';

-- 用户标签关联表
CREATE TABLE IF NOT EXISTS `user_interest_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `weight` DECIMAL(3,1) DEFAULT 0.0 COMMENT '标签权重',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tag` (`user_id`, `tag_id`),
    CONSTRAINT `fk_user_interest_tag_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_user_interest_tag_tag_id` FOREIGN KEY (`tag_id`) REFERENCES `interest_tag` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户标签关联表';

-- 推荐记录表
CREATE TABLE IF NOT EXISTS `recommendation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `lecture_id` BIGINT NOT NULL COMMENT '讲座ID',
    `recommend_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推荐时间',
    `match_score` DECIMAL(3,1) NOT NULL COMMENT '匹配分数',
    `channel` TINYINT NOT NULL COMMENT '推荐渠道(1:首页 2:邮件 3:短信)',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态(0:未点击 1:已点击 2:已报名)',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除 1:已删除)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_recommendation_log_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_recommendation_log_lecture_id` FOREIGN KEY (`lecture_id`) REFERENCES `lecture` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐记录表';
-- ============================================================
-- Agent 异步任务表（任务型 Agent：大数据量分析转后台执行）
-- ============================================================
CREATE TABLE IF NOT EXISTS `agent_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `user_id` BIGINT NOT NULL COMMENT '发起用户ID',
    `type` VARCHAR(50) NOT NULL COMMENT '任务类型(如 evaluation_analysis)',
    `params` VARCHAR(500) DEFAULT NULL COMMENT '任务参数(JSON)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING/RUNNING/SUCCESS/FAILED)',
    `progress` INT NOT NULL DEFAULT 0 COMMENT '进度百分比(0-100)',
    `progress_text` VARCHAR(255) DEFAULT NULL COMMENT '进度说明',
    `result` TEXT COMMENT '任务结果(文本报告)',
    `error` VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `finished_time` DATETIME DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_agent_task_user` (`user_id`, `created_time`),
    CONSTRAINT `fk_agent_task_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 异步任务表';

-- ============================================================
-- 已有库升级语句（新装环境无需执行，建表语句已包含）
-- ============================================================
-- 2026-09-11：报名表增加签到时间字段
-- ALTER TABLE `registration` ADD COLUMN `checkin_time` DATETIME DEFAULT NULL COMMENT '签到时间' AFTER `checkin_status`;
