package com.zzy.finsight.service.impl;

import com.zzy.finsight.agent.runtime.LegacyStockReportTraceReader;
import com.zzy.finsight.dto.stock.StockBadCaseFeedbackRequest;
import com.zzy.finsight.dto.stock.StockReportReplayResponse;
import com.zzy.finsight.dto.stock.StockReportTraceResponse;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockReportServiceImplTest {
    private FinancialSnapshotMapper snapshotMapper;
    private LegacyStockReportTraceReader traceReader;
    private StockReportServiceImpl service;

    @BeforeEach
    void setUp() {
        snapshotMapper = mock(FinancialSnapshotMapper.class);
        traceReader = mock(LegacyStockReportTraceReader.class);
        service = new StockReportServiceImpl(snapshotMapper, traceReader);
    }

    @Test
    void keepsLegacyFeedbackAndReplayAvailable() {
        StockBadCaseFeedbackRequest feedback = new StockBadCaseFeedbackRequest();
        feedback.setFeedbackType("CITATION_ERROR");
        feedback.setDetail("引用对应错误");
        StockReportReplayResponse replay = new StockReportReplayResponse(11L, "{}", List.of(), List.of());
        when(snapshotMapper.findReplay(7L, 11L)).thenReturn(Optional.of(replay));

        service.saveFeedback(7L, 11L, feedback);

        verify(snapshotMapper).saveFeedback(7L, 11L, feedback);
        assertThat(service.replay(7L, 11L)).isSameAs(replay);
    }

    @Test
    void rejectsMissingLegacyReplay() {
        when(snapshotMapper.findReplay(7L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replay(7L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到股票报告回放快照");
    }

    @Test
    void delegatesLegacyTraceRead() {
        StockReportTraceResponse trace = mock(StockReportTraceResponse.class);
        when(traceReader.get(7L, 11L)).thenReturn(trace);

        assertThat(service.trace(7L, 11L)).isSameAs(trace);
    }
}
