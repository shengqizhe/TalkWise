package com.example.lecture.agent.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.example.lecture.entity.AgentTask;
import com.example.lecture.mapper.AgentTaskMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 调度 SQL 的数据库级验证：CAS 抢占、优先级排序、退避时间门控、终态与取消竞态。
 *
 * <p>自包含（H2 MySQL 模式 + 手工装配 MyBatis），不启动 Spring 上下文，
 * 因此不依赖 MAIL_USERNAME 等外部占位符；DDL 与 {@code schema.sql} 的 agent_task 保持一致。
 */
class AgentTaskScheduleDbTest {

    private static final String CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS `user` (
                id BIGINT NOT NULL AUTO_INCREMENT,
                PRIMARY KEY (id)
            )
            """;

    private static final String CREATE_TASK_SQL = """
            CREATE TABLE IF NOT EXISTS `agent_task` (
                id BIGINT NOT NULL AUTO_INCREMENT,
                user_id BIGINT NOT NULL,
                type VARCHAR(50) NOT NULL,
                idempotency_key VARCHAR(100) DEFAULT NULL,
                name VARCHAR(200) DEFAULT NULL,
                params VARCHAR(500) DEFAULT NULL,
                priority INT NOT NULL DEFAULT 0,
                retry_count INT NOT NULL DEFAULT 0,
                max_retries INT NOT NULL DEFAULT 2,
                next_retry_time DATETIME DEFAULT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                progress INT NOT NULL DEFAULT 0,
                progress_text VARCHAR(255) DEFAULT NULL,
                result TEXT,
                error VARCHAR(500) DEFAULT NULL,
                created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                started_time DATETIME DEFAULT NULL,
                finished_time DATETIME DEFAULT NULL,
                PRIMARY KEY (id),
                CONSTRAINT uk_agent_task_idempotency UNIQUE (idempotency_key)
            )
            """;

    private static final String CREATE_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_agent_task_schedule ON agent_task (status, priority, next_retry_time, created_time)";

    private SqlSessionFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        // 每个用例一个独立内存库（库名用 UUID，防止并行执行时碰撞）
        dataSource.setURL("jdbc:h2:mem:sched-" + java.util.UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");

        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(CREATE_SQL);
            st.execute(CREATE_TASK_SQL);
            st.execute(CREATE_INDEX_SQL);
            st.execute("INSERT INTO `user` (id) VALUES (1)");
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentTaskMapper.class);

        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setConfiguration(configuration);
        this.factory = bean.getObject();
    }

    private AgentTaskMapper mapper(SqlSession session) {
        return session.getMapper(AgentTaskMapper.class);
    }

    @AfterEach
    void tearDown() {
        factory = null;
    }

    // ---------- 优先级与退避排序 ----------

    @Test
    void selectsByPriorityDescThenCreatedTimeAsc() {
        try (SqlSession session = factory.openSession(true)) {
            AgentTaskMapper mapper = mapper(session);
            mapper.insert(task("low", 0, at("09:00"), null));
            mapper.insert(task("high-late", 10, at("11:00"), null));
            mapper.insert(task("high-early", 10, at("08:00"), null));

            List<AgentTask> picked = mapper.selectSchedulable(now(), 10);

            assertEquals(List.of("high-early", "high-late", "low"),
                    picked.stream().map(AgentTask::getName).toList());
        }
    }

    @Test
    void skipsTasksWhoseRetryBackoffHasNotElapsed() {
        try (SqlSession session = factory.openSession(true)) {
            AgentTaskMapper mapper = mapper(session);
            mapper.insert(task("due-now", 0, at("08:00"), null));
            mapper.insert(task("due-past", 0, at("08:00"), null));
            mapper.insert(task("due-future", 5, at("08:00"), null));
            // 仅 due-future 处于退避窗口内（优先级最高但尚未到期）
            mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getName, "due-past")
                    .set(AgentTask::getNextRetryTime, now().minusMinutes(1)));
            mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getName, "due-future")
                    .set(AgentTask::getNextRetryTime, now().plusMinutes(10)));

            List<AgentTask> picked = mapper.selectSchedulable(now(), 10);

            // 未来才到期的任务即使优先级更高也不得被取出
            assertEquals(List.of("due-now", "due-past"),
                    picked.stream().map(AgentTask::getName).toList());
        }
    }

    @Test
    void ignoresNonPendingTasksAndRespectsLimit() {
        try (SqlSession session = factory.openSession(true)) {
            AgentTaskMapper mapper = mapper(session);
            AgentTask running = task("running", 100, at("07:00"), null);
            running.setStatus(AgentTask.STATUS_RUNNING);
            mapper.insert(running);
            AgentTask success = task("success", 100, at("07:00"), null);
            success.setStatus(AgentTask.STATUS_SUCCESS);
            mapper.insert(success);
            mapper.insert(task("p1", 1, at("07:00"), null));
            mapper.insert(task("p2", 2, at("07:00"), null));

            List<AgentTask> picked = mapper.selectSchedulable(now(), 1);

            assertEquals(1, picked.size());
            assertEquals("p2", picked.get(0).getName());
        }
    }

    // ---------- CAS 抢占 ----------

    @Test
    void claimSucceedsOnceThenFailsForConcurrentPoller() {
        try (SqlSession session = factory.openSession(true)) {
            AgentTaskMapper mapper = mapper(session);
            Long id = mapper.insert(task("only-once", 0, at("08:00"), null)) == 1
                    ? lastId(session) : null;

            assertEquals(1, mapper.claimPending(id, now()));
            // 第二次抢占（模拟另一个实例/下一轮轮询）必须失败，杜绝重复执行
            assertEquals(0, mapper.claimPending(id, now()));

            AgentTask claimed = mapper.selectById(id);
            assertEquals(AgentTask.STATUS_RUNNING, claimed.getStatus());
            assertNull(claimed.getNextRetryTime());
        }
    }

    @Test
    void claimSkipsCancelledTask() {
        try (SqlSession session = factory.openSession(true)) {
            AgentTaskMapper mapper = mapper(session);
            mapper.insert(task("cancelled", 0, at("08:00"), null));
            Long id = lastId(session);
            AgentTask cancel = new AgentTask();
            cancel.setId(id);
            cancel.setStatus(AgentTask.STATUS_CANCELLED);
            mapper.updateById(cancel);

            assertEquals(0, mapper.claimPending(id, now()));
            assertEquals(AgentTask.STATUS_CANCELLED, mapper.selectById(id).getStatus());
        }
    }

    // ---------- 失败重排与终态 ----------

    @Test
    void releaseForRetryOnlyAppliesToRunningTask() {
        try (SqlSession session = factory.openSession(true)) {
            AgentTaskMapper mapper = mapper(session);
            mapper.insert(task("retryable", 0, at("08:00"), null));
            Long id = lastId(session);
            LocalDateTime nextRetry = now().plusSeconds(30);
            mapper.claimPending(id, now());

            assertEquals(1, mapper.releaseForRetry(id, 1, nextRetry, "第一次失败"));

            AgentTask task = mapper.selectById(id);
            assertEquals(AgentTask.STATUS_PENDING, task.getStatus());
            assertEquals(1, task.getRetryCount());
            assertEquals(nextRetry, task.getNextRetryTime());
            assertEquals("第一次失败", task.getError());
            assertNull(task.getFinishedTime());

            // 已在 PENDING：再次重排不生效（只有 RUNNING 可重排）
            assertEquals(0, mapper.releaseForRetry(id, 2, nextRetry, "重复调用"));
            assertEquals(1, mapper.selectById(id).getRetryCount());
        }
    }

    @Test
    void markFailedOnlyAppliesToRunningTask() {
        try (SqlSession session = factory.openSession(true)) {
            AgentTaskMapper mapper = mapper(session);
            mapper.insert(task("exhausted", 0, at("08:00"), null));
            Long id = lastId(session);
            mapper.claimPending(id, now());

            assertEquals(1, mapper.markFailed(id, 2, "重试耗尽", now()));

            AgentTask task = mapper.selectById(id);
            assertEquals(AgentTask.STATUS_FAILED, task.getStatus());
            assertEquals(2, task.getRetryCount());
            assertEquals("重试耗尽", task.getError());
            assertNotNull(task.getFinishedTime());
        }
    }

    @Test
    void successAndFailureDoNotOverwriteCancelledTask() {
        try (SqlSession session = factory.openSession(true)) {
            AgentTaskMapper mapper = mapper(session);
            mapper.insert(task("raced", 0, at("08:00"), null));
            Long id = lastId(session);
            mapper.claimPending(id, now());

            // 用户在任务执行中被取消
            AgentTask cancel = new AgentTask();
            cancel.setId(id);
            cancel.setStatus(AgentTask.STATUS_CANCELLED);
            mapper.updateById(cancel);

            // 执行器的成功/失败/进度写入都不得复活已取消的任务
            assertEquals(0, mapper.markSuccess(id, "结果", now()));
            assertEquals(0, mapper.releaseForRetry(id, 1, now().plusSeconds(30), "err"));
            assertEquals(0, mapper.markFailed(id, 2, "err", now()));
            assertEquals(0, mapper.updateProgress(id, 50, "执行中", now()));
            assertEquals(AgentTask.STATUS_CANCELLED, mapper.selectById(id).getStatus());
        }
    }

    // ---------- 进度与统计 ----------

    @Test
    void progressUpdatesOnlyWhileRunningAndCountsByStatus() {
        try (SqlSession session = factory.openSession(true)) {
            AgentTaskMapper mapper = mapper(session);
            mapper.insert(task("job", 0, at("08:00"), null));
            Long id = lastId(session);

            // 尚未抢占（PENDING）：进度写入不生效
            assertEquals(0, mapper.updateProgress(id, 40, "早期写入", now()));

            mapper.claimPending(id, now());
            assertEquals(1, mapper.updateProgress(id, 40, "正在分析", now()));
            AgentTask task = mapper.selectById(id);
            assertEquals(40, task.getProgress());
            assertEquals("正在分析", task.getProgressText());
            assertNotNull(task.getStartedTime());

            assertEquals(1, mapper.countByStatus(AgentTask.STATUS_RUNNING));
            assertEquals(0, mapper.countByStatus(AgentTask.STATUS_FAILED));
        }
    }

    // ---------- 幂等唯一索引（并发兜底防线） ----------

    @Test
    void uniqueIndexRejectsDuplicateIdempotencyKey() {
        try (SqlSession session = factory.openSession(true)) {
            AgentTaskMapper mapper = mapper(session);
            mapper.insert(task("first", 0, at("08:00"), "u1:key-1"));

            // 同键再插入：由唯一索引 uk_agent_task_idempotency 拦截
            assertThrows(RuntimeException.class,
                    () -> mapper.insert(task("second", 0, at("08:01"), "u1:key-1")));

            // 不同用户同业务键不冲突
            mapper.insert(task("other-user", 0, at("08:02"), "u2:key-1"));
            assertEquals(1, mapper.selectList(null).stream()
                    .filter(t -> "other-user".equals(t.getName())).count());
        }
    }

    private AgentTask task(String name, int priority, LocalDateTime createdTime, String idempotencyKey) {
        AgentTask task = new AgentTask();
        task.setUserId(1L);
        task.setType(AgentTask.TYPE_REPORT_GENERATION);
        task.setName(name);
        task.setPriority(priority);
        task.setRetryCount(0);
        task.setMaxRetries(2);
        task.setStatus(AgentTask.STATUS_PENDING);
        task.setProgress(0);
        task.setCreatedTime(createdTime);
        task.setIdempotencyKey(idempotencyKey);
        return task;
    }

    private Long lastId(SqlSession session) {
        try (Statement st = session.getConnection().createStatement();
             var rs = st.executeQuery("SELECT MAX(id) FROM agent_task")) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private LocalDateTime at(String time) {
        return LocalDateTime.of(2026, 9, 13, Integer.parseInt(time.substring(0, 2)),
                Integer.parseInt(time.substring(3, 5)));
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 9, 13, 12, 0, 0);
    }
}
