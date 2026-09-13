-- ============================================================
-- 知讲 TalkWise · 数据库升级脚本
-- 版本：2026-09-13（阶段 D：容量规划基础字段）
-- 适用：已有库 university_lecture_management（老库增量升级）
--
-- 执行方式（任选其一）：
--   命令行：mysql -u root -p university_lecture_management < upgrade-2026-09-13.sql
--   客户端：Navicat / DataGrip / Workbench 中打开本文件，整体执行
--
-- 说明：
--   1. 语句已按依赖顺序排列，可自上而下一次性执行。
--   2. "列已存在"（Duplicate column name / Duplicate key name）报错说明该步
--      之前已执行过，可忽略；如需完全幂等，见文件末尾的幂等版。
--   3. 全新库请直接用 schema.sql + data.sql，不需要本脚本。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 新增学校画像表
--    （必须放在最前：后面没有依赖，但保证逻辑上先建表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `school_profile`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '学校画像ID',
    `school_name`       VARCHAR(200) NOT NULL COMMENT '学校名称',
    `profile_content`   TEXT         DEFAULT NULL COMMENT '办学定位、优势学科与学生群体画像',
    `min_room_capacity` INT          NOT NULL DEFAULT 0 COMMENT '地点最小容量',
    `max_room_capacity` INT          NOT NULL DEFAULT 0 COMMENT '地点最大容量',
    `created_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_school_profile_name` (`school_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='学校画像表';


-- ------------------------------------------------------------
-- 2. location：新增 school_name（所属学校）与 capacity（地点容量）
--    capacity 带 DEFAULT 0，避免老数据/后续插入缺省时插入失败
-- ------------------------------------------------------------
ALTER TABLE `location`
    ADD COLUMN `school_name` VARCHAR(200) DEFAULT NULL COMMENT '所属学校名称' AFTER `name`,
    ADD COLUMN `capacity`    INT          NOT NULL DEFAULT 0 COMMENT '地点容量' AFTER `type`;

-- 学校名用于画像容量汇总，建索引
ALTER TABLE `location`
    ADD INDEX `idx_location_school_name` (`school_name`);


-- ------------------------------------------------------------
-- 3. user：新增职称与个人简介（容量评估"讲师声望"维度的输入）
-- ------------------------------------------------------------
ALTER TABLE `user`
    ADD COLUMN `title` VARCHAR(100) DEFAULT NULL COMMENT '职称或头衔' AFTER `department_id`,
    ADD COLUMN `bio`   TEXT         DEFAULT NULL COMMENT '个人简介' AFTER `title`;


-- ------------------------------------------------------------
-- 4. 数据初始化（可选，按需执行）
-- ------------------------------------------------------------

-- 4.1 老库已有地点：补学校名称，否则画像汇总拿不到这些地点
--     （请把 '示例大学' 换成本校实际名称）
UPDATE `location`
SET `school_name` = '示例大学'
WHERE `school_name` IS NULL;

-- 4.2 老库已有地点：容量默认补 0，画像汇总会过滤 capacity > 0，
--     因此需要管理员在前端「地点管理」里逐条填写真实容量后才会生效。
--     如想先给一个占位值，可执行下面这句（150 只是示例，按需修改）：
-- UPDATE `location` SET `capacity` = 150 WHERE `capacity` = 0;

-- 4.3 初始化学校画像单行记录（若表内已有则更新，不会重复插入）
INSERT INTO `school_profile` (`school_name`, `profile_content`, `min_room_capacity`, `max_room_capacity`)
VALUES ('示例大学', '学校办学定位、优势学科与学生群体画像待管理员维护', 100, 200)
ON DUPLICATE KEY UPDATE `updated_time` = CURRENT_TIMESTAMP;


-- ------------------------------------------------------------
-- 5. 执行后自检
-- ------------------------------------------------------------
-- 5.1 确认 location 新列存在
SHOW COLUMNS FROM `location` LIKE 'capacity';
SHOW COLUMNS FROM `location` LIKE 'school_name';
-- 5.2 确认 user 新列存在
SHOW COLUMNS FROM `user` LIKE 'title';
SHOW COLUMNS FROM `user` LIKE 'bio';
-- 5.3 确认 school_profile 表与数据
SELECT `school_name`, `min_room_capacity`, `max_room_capacity` FROM `school_profile`;


-- ============================================================
-- 附：幂等版（可重复执行，适合反复调试）
-- MySQL 不支持 ADD COLUMN IF NOT EXISTS，因此用存储过程判存在性。
-- 需要在支持 DELIMITER 的客户端执行；如已成功跑完上面 1~3 步，无需再跑。
-- ============================================================
-- DROP PROCEDURE IF EXISTS `add_column_if_absent`;
-- DELIMITER $$
-- CREATE PROCEDURE `add_column_if_absent`(
--     IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
-- BEGIN
--     IF NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
--                   WHERE TABLE_SCHEMA = DATABASE()
--                     AND TABLE_NAME = p_table AND COLUMN_NAME = p_column) THEN
--         SET @s = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
--         PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
--     END IF;
-- END$$
-- DELIMITER ;
--
-- CALL add_column_if_absent('location', 'school_name',
--     '`school_name` VARCHAR(200) DEFAULT NULL COMMENT ''所属学校名称'' AFTER `name`');
-- CALL add_column_if_absent('location', 'capacity',
--     '`capacity` INT NOT NULL DEFAULT 0 COMMENT ''地点容量'' AFTER `type`');
-- CALL add_column_if_absent('user', 'title',
--     '`title` VARCHAR(100) DEFAULT NULL COMMENT ''职称或头衔'' AFTER `department_id`');
-- CALL add_column_if_absent('user', 'bio',
--     '`bio` TEXT DEFAULT NULL COMMENT ''个人简介'' AFTER `title`');
-- DROP PROCEDURE IF EXISTS `add_column_if_absent`;
