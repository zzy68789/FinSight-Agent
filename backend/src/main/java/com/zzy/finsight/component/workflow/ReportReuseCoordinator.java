package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.ReusableReportRecord;
import com.zzy.finsight.service.ReportService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 协调同用户同生成上下文的历史复用、并发合并和主请求结果发布。
 */
@Component
public class ReportReuseCoordinator {
    private final ReportService reportService;
    private final ReportGenerationSingleFlight singleFlight;
    private final MeterRegistry meterRegistry;
    private final Duration waitTimeout;

    public ReportReuseCoordinator(
            ReportService reportService,
            ReportGenerationSingleFlight singleFlight,
            MeterRegistry meterRegistry,
            @Value("${finsight.workflow.singleflight-wait-timeout:PT7M}") Duration waitTimeout
    ) {
        this.reportService = reportService;
        this.singleFlight = singleFlight;
        this.meterRegistry = meterRegistry;
        this.waitTimeout = waitTimeout == null || waitTimeout.isZero() || waitTimeout.isNegative()
                ? Duration.ofMinutes(7)
                : waitTimeout;
    }

    /**
     * 优先返回通过当前规则复验的缓存；未命中时仅允许一个主请求执行生成函数。
     */
    public ReuseOutcome coordinate(
            long ownerId,
            String generationContextHash,
            CandidateValidator validator,
            Supplier<GeneratedReport> generator,
            Runnable waitingCallback
    ) {
        Optional<ReusableReportRecord> historical = reportService.findReusable(ownerId, generationContextHash);
        if (historical.isPresent()
                && validator.validate(historical.orElseThrow(), ReuseOrigin.HISTORICAL)) {
            meterRegistry.counter("finsight.stock.report.cache", "result", "hit").increment();
            return new ReuseOutcome(historical.orElseThrow(), ReuseOrigin.HISTORICAL, 0);
        }
        meterRegistry.counter("finsight.stock.report.cache", "result", "miss").increment();

        while (true) {
            ReportGenerationSingleFlight.Flight flight = singleFlight.acquire(ownerId, generationContextHash);
            if (flight.leader()) {
                meterRegistry.counter("finsight.stock.report.singleflight", "result", "leader").increment();
                return generateAsLeader(flight, generator);
            }

            waitingCallback.run();
            meterRegistry.counter("finsight.stock.report.singleflight", "result", "follower").increment();
            long waitStartedAt = System.nanoTime();
            Optional<ReusableReportRecord> joined = singleFlight.await(flight, waitTimeout);
            Timer.builder("finsight.stock.report.singleflight.wait")
                    .register(meterRegistry)
                    .record(Math.max(0L, System.nanoTime() - waitStartedAt), TimeUnit.NANOSECONDS);
            if (joined.isPresent() && validator.validate(joined.orElseThrow(), ReuseOrigin.COALESCED)) {
                meterRegistry.counter("finsight.stock.report.cache", "result", "hit").increment();
                return new ReuseOutcome(joined.orElseThrow(), ReuseOrigin.COALESCED, 0);
            }
            meterRegistry.counter("finsight.stock.report.singleflight", "result", "retry").increment();
        }
    }

    private ReuseOutcome generateAsLeader(
            ReportGenerationSingleFlight.Flight flight,
            Supplier<GeneratedReport> generator
    ) {
        try {
            GeneratedReport generated = generator.get();
            ReusableReportRecord report = generated.report();
            if ("PASS".equals(report.reviewStatus()) && report.id() > 0L) {
                singleFlight.complete(flight, report);
            } else {
                singleFlight.completeWithoutReusable(flight);
            }
            return new ReuseOutcome(report, ReuseOrigin.GENERATED, generated.rewriteCount());
        } catch (RuntimeException | Error e) {
            singleFlight.fail(flight);
            throw e;
        }
    }

    /** 校验候选缓存是否仍满足当前任务的数据和质量门禁。 */
    @FunctionalInterface
    public interface CandidateValidator {
        boolean validate(ReusableReportRecord report, ReuseOrigin origin);
    }

    /** 报告结果来源。 */
    public enum ReuseOrigin {
        HISTORICAL,
        COALESCED,
        GENERATED
    }

    /**
     * @param report 已持久化的报告结果。
     * @param rewriteCount Writer 重写次数。
     */
    public record GeneratedReport(ReusableReportRecord report, int rewriteCount) {
    }

    /**
     * @param report 最终采用的报告。
     * @param origin 报告来源。
     * @param rewriteCount Writer 重写次数。
     */
    public record ReuseOutcome(
            ReusableReportRecord report,
            ReuseOrigin origin,
            int rewriteCount
    ) {
        /** 返回本次任务是否复用了已有报告。 */
        public boolean reused() {
            return origin != ReuseOrigin.GENERATED;
        }
    }
}
