package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.ReusableReportRecord;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 在单个应用实例内合并同一用户、同一生成上下文的并发报告生成请求。
 */
@Component
public class ReportGenerationSingleFlight {
    private final ConcurrentMap<FlightKey, CompletableFuture<Optional<ReusableReportRecord>>> inFlight =
            new ConcurrentHashMap<>();

    /**
     * 申请指定生成上下文的执行权，首个请求成为主请求，其余请求等待主请求结果。
     */
    public Flight acquire(long ownerId, String generationContextHash) {
        if (generationContextHash == null || generationContextHash.isBlank()) {
            throw new IllegalArgumentException("报告生成上下文指纹不能为空");
        }
        FlightKey key = new FlightKey(ownerId, generationContextHash);
        CompletableFuture<Optional<ReusableReportRecord>> created = new CompletableFuture<>();
        CompletableFuture<Optional<ReusableReportRecord>> existing = inFlight.putIfAbsent(key, created);
        return existing == null
                ? new Flight(true, key, created)
                : new Flight(false, key, existing);
    }

    /** 等待主请求产出可复用报告；主请求未通过门控时返回空。 */
    public Optional<ReusableReportRecord> await(Flight flight) {
        if (flight == null) {
            throw new IllegalArgumentException("单飞凭证不能为空");
        }
        return flight.result.join();
    }

    /** 发布主请求生成且通过门控的报告。 */
    public void complete(Flight flight, ReusableReportRecord report) {
        if (report == null) {
            throw new IllegalArgumentException("可复用报告不能为空");
        }
        finish(flight, Optional.of(report));
    }

    /** 主请求未产出可复用报告时释放执行权，允许等待方重新竞选。 */
    public void completeWithoutReusable(Flight flight) {
        finish(flight, Optional.empty());
    }

    /** 主请求异常时释放执行权，等待方可重新生成。 */
    public void fail(Flight flight) {
        finish(flight, Optional.empty());
    }

    private void finish(Flight flight, Optional<ReusableReportRecord> result) {
        if (flight == null || !flight.leader) {
            throw new IllegalArgumentException("只有主请求可以结束单飞任务");
        }
        if (inFlight.remove(flight.key, flight.result)) {
            flight.result.complete(result);
        }
    }

    /**
     * 单飞申请结果。
     */
    public static final class Flight {
        private final boolean leader;
        private final FlightKey key;
        private final CompletableFuture<Optional<ReusableReportRecord>> result;

        private Flight(
                boolean leader,
                FlightKey key,
                CompletableFuture<Optional<ReusableReportRecord>> result
        ) {
            this.leader = leader;
            this.key = key;
            this.result = result;
        }

        /** 当前请求是否获得主请求执行权。 */
        public boolean leader() {
            return leader;
        }
    }

    /**
     * @param ownerId 用户标识
     * @param generationContextHash 报告生成上下文指纹
     */
    private record FlightKey(long ownerId, String generationContextHash) {
    }
}
