package com.zzy.finsight.component.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.domain.WorkflowCheckpointRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowCheckpointCodecTest {
    private final WorkflowCheckpointCodec codec = new WorkflowCheckpointCodec(new ObjectMapper());

    @Test
    void parsesWriterAndReviewerCheckpoint() {
        WorkflowCheckpointRecord writer = checkpoint("WRITER", 2, """
                {"attempt":2,"finalReport":"恢复报告"}
                """);
        WorkflowCheckpointRecord reviewer = checkpoint("REVIEWER", 2, """
                {
                  "attempt":2,
                  "reviewStatus":"FAIL",
                  "critique":"缺少引用",
                  "compliance":{"status":"PASS","score":100.00,"issues":[]}
                }
                """);

        assertThat(codec.writer(writer)).get()
                .extracting(WriterCheckpointState::attempt, WriterCheckpointState::report)
                .containsExactly(2, "恢复报告");
        assertThat(codec.reviewer(reviewer)).get()
                .satisfies(state -> {
                    assertThat(state.attempt()).isEqualTo(2);
                    assertThat(state.review().status()).isEqualTo("FAIL");
                    assertThat(state.review().reason()).isEqualTo("缺少引用");
                    assertThat(state.compliance().status()).isEqualTo("PASS");
                });
    }

    @Test
    void rejectsMalformedOrIncompleteCheckpoint() {
        assertThat(codec.writer(checkpoint("WRITER", 1, "{invalid-json"))).isEmpty();
        assertThat(codec.writer(checkpoint("WRITER", 1, "{\"attempt\":1,\"finalReport\":\"\"}"))).isEmpty();
        assertThat(codec.reviewer(checkpoint("REVIEWER", 4, "{}"))).isEmpty();
    }

    private WorkflowCheckpointRecord checkpoint(String stage, int attemptNo, String stateJson) {
        return new WorkflowCheckpointRecord(
                1L,
                "stock-thread",
                11L,
                stage,
                attemptNo,
                "context-hash",
                stateJson,
                LocalDateTime.of(2026, 7, 17, 10, 0)
        );
    }
}
