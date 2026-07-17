package com.zzy.finsight.mapper;

import com.zzy.finsight.component.workflow.WorkflowStagePersistence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 在真实 MySQL 上验证 Flyway 迁移、任务租约、检查点往返和报告租户隔离。
 */
@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "finsight.market.tushare.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class MySqlPersistenceIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("finsight")
            .withUsername("finsight")
            .withPassword("finsight");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ResearchTaskMapper taskMapper;
    @Autowired
    private CheckpointMapper checkpointMapper;
    @Autowired
    private ReportMapper reportMapper;
    @Autowired
    private WorkflowStagePersistence stagePersistence;

    @Test
    void migratesAndPersistsWorkflowReliabilityContracts() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version = '3'",
                Integer.class
        );
        assertThat(migrationCount).isEqualTo(1);

        long taskId = taskMapper.create(
                7L, "mysql-integration-thread", "分析 600519", "stock-hybrid", "{\"ticker\":\"600519\"}"
        );
        LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(5);
        assertThat(taskMapper.startAttempt(taskId, "runner-a", leaseUntil)).isTrue();
        assertThat(taskMapper.startAttempt(taskId, "runner-b", leaseUntil)).isFalse();

        checkpointMapper.save(
                "mysql-integration-thread",
                taskId,
                "WRITER",
                2,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                Map.of("finalReport", "检查点报告", "attempt", 2)
        );
        assertThat(checkpointMapper.findLatest(
                taskId,
                "WRITER",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        )).hasValueSatisfying(checkpoint -> {
            assertThat(checkpoint.attemptNo()).isEqualTo(2);
            assertThat(checkpoint.stateJson()).contains("检查点报告");
        });

        long reportId = reportMapper.save(
                7L,
                taskId,
                "mysql-integration-thread",
                "已过评审报告",
                "PASS",
                "",
                null,
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                null
        );
        assertThat(reportMapper.findReusable(
                7L, "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        )).hasValueSatisfying(report -> assertThat(report.id()).isEqualTo(reportId));
        assertThat(reportMapper.findReusable(
                8L, "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        )).isEmpty();

        reportMapper.softDelete(7L, reportId);
        assertThat(reportMapper.findReusable(
                7L, "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        )).isEmpty();

        long taskWithoutLease = taskMapper.create(
                7L, "rollback-thread", "验证事务回滚", "stock-hybrid", "{\"ticker\":\"600519\"}"
        );
        assertThatThrownBy(() -> stagePersistence.persist(new WorkflowStagePersistence.StageCommit(
                taskWithoutLease,
                "rollback-thread",
                "writer",
                "WRITER",
                Map.of("finalReport", "不应落库"),
                1L,
                1,
                "SUCCESS",
                null,
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                "missing-runner",
                LocalDateTime.now().plusMinutes(5)
        ))).isInstanceOf(IllegalStateException.class).hasMessageContaining("租约已失效");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_step_log WHERE task_id = ?", Integer.class, taskWithoutLease
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM checkpoint WHERE task_id = ?", Integer.class, taskWithoutLease
        )).isZero();
    }
}
