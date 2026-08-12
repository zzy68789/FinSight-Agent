package com.zzy.finsight.mapper;

import com.zzy.finsight.agent.event.AgentEvent;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.planning.PlannerOutput;
import com.zzy.finsight.agent.runtime.LeaseToken;
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

/**
 * 在真实 MySQL 上验证 Agent 迁移、任务租约、轮次工具轨迹、检查点和报告租户隔离。
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
    private AgentRuntimeMapper agentRuntimeMapper;
    @Autowired
    private AgentPlannerCallMapper plannerCallMapper;
    @Autowired
    private AgentEventOutboxMapper eventOutboxMapper;

    @Test
    void migratesAndPersistsAgentReliabilityContracts() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version = '6'",
                Integer.class
        );
        assertThat(migrationCount).isEqualTo(1);

        long taskId = taskMapper.createAgent(
                7L,
                "mysql-integration-thread",
                "分析 600519 的盈利质量",
                "agent-hybrid",
                "{\"ticker\":\"600519\"}",
                "planner-v1",
                "toolset-v1",
                "policy-v1"
        );
        LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(5);
        LeaseToken lease = taskMapper.startAttemptToken(taskId, "runner-a", leaseUntil).orElseThrow();
        assertThat(lease.epoch()).isEqualTo(1L);
        assertThat(taskMapper.startAttempt(taskId, "runner-b", leaseUntil)).isFalse();

        plannerCallMapper.save(taskId, new PlannerOutput<>(
                Map.of("type", "CALL_TOOL"), false, "", 12, 3, 40L,
                "NEXT_ACTION", "FAST", "qwen-fast", 1, true
        ), true);
        assertThat(plannerCallMapper.findByTaskId(taskId)).singleElement()
                .satisfies(call -> {
                    assertThat(call.actualModel()).isEqualTo("qwen-fast");
                    assertThat(call.routeCorrect()).isTrue();
                });

        long eventSequence = taskMapper.nextEventSequence(lease);
        AgentEvent event = new AgentEvent(
                0L, AgentEvent.CURRENT_SCHEMA_VERSION, taskId, "mysql-integration-thread",
                eventSequence, "event-mysql-1", 0, "run_created", "SUCCESS",
                Map.of("ticker", "600519"), "", 0L, null, LocalDateTime.now()
        );
        long eventId = eventOutboxMapper.insert(event);
        assertThat(eventId).isPositive();
        assertThat(eventOutboxMapper.findByTaskId(taskId)).singleElement()
                .satisfies(saved -> {
                    assertThat(saved.sequence()).isEqualTo(1L);
                    assertThat(saved.payload()).containsEntry("ticker", "600519");
                });

        long turnId = agentRuntimeMapper.saveTurnFenced(
                lease,
                1,
                "RESEARCH",
                "CALL_TOOLS",
                Map.of("toolCalls", 1),
                10,
                5,
                0L
        );
        long toolCallId = agentRuntimeMapper.startToolCallFenced(
                lease,
                turnId,
                "call-1",
                "get_financial_statements",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                Map.of("ticker", "600519"),
                1
        );
        agentRuntimeMapper.completeToolCall(
                toolCallId,
                Map.of("evidenceCount", 3),
                "SUCCESS",
                12L,
                null,
                null,
                LocalDateTime.now()
        );
        agentRuntimeMapper.completeTurn(turnId, "已补充财务报表证据", "SUCCESS", 18L);

        assertThat(agentRuntimeMapper.findTurns(taskId)).singleElement()
                .satisfies(turn -> assertThat(turn.actionType()).isEqualTo("CALL_TOOLS"));
        assertThat(agentRuntimeMapper.findToolCalls(taskId)).singleElement()
                .satisfies(call -> assertThat(call.status()).isEqualTo("SUCCESS"));

        AgentState checkpointState = new AgentState();
        checkpointState.setTaskId(taskId);
        checkpointState.setThreadId("mysql-integration-thread");
        checkpointState.setTurnNo(1);
        checkpointState.setPhase("RESEARCH");
        checkpointState.setContextHash("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        checkpointMapper.saveAgent(
                "mysql-integration-thread",
                taskId,
                1,
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                checkpointState
        );
        assertThat(checkpointMapper.findLatest(
                taskId,
                "AGENT_STATE",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        )).hasValueSatisfying(checkpoint -> {
            assertThat(checkpoint.attemptNo()).isEqualTo(1);
            assertThat(checkpoint.stateVersion()).isEqualTo("agent-state-v3-lite");
            assertThat(checkpoint.stateJson()).contains("RESEARCH");
        });

        long reportId = reportMapper.save(
                7L,
                taskId,
                "mysql-integration-thread",
                "已过评审报告",
                "PASS",
                "",
                null,
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                null
        );
        assertThat(reportMapper.findReusable(
                7L, "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        )).hasValueSatisfying(report -> assertThat(report.id()).isEqualTo(reportId));
        assertThat(reportMapper.findReusable(
                8L, "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        )).isEmpty();

        reportMapper.softDelete(7L, reportId);
        assertThat(reportMapper.findReusable(
                7L, "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        )).isEmpty();

        assertThat(taskMapper.updateAgentProgressFenced(
                lease, "SYNTHESIS", 1, 1, LocalDateTime.now().plusMinutes(5)
        )).isTrue();
    }
}
