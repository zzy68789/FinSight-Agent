package com.zzy.finsight.component.analysis;

import com.zzy.finsight.domain.stock.StockSubject;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 规范化并校验一次研究允许使用的可比证券集合。
 */
@Component
public class ComparisonTickerPolicy {
    private static final int MAX_COMPARISONS = 3;
    private final StockCodeResolver stockCodeResolver;

    public ComparisonTickerPolicy(StockCodeResolver stockCodeResolver) {
        this.stockCodeResolver = stockCodeResolver;
    }

    /** 返回去重后的同资产类型规范化代码，拒绝主证券和超限输入。 */
    public List<String> normalize(
            StockSubject primarySubject,
            List<String> requestedTickers,
            String researchDepth
    ) {
        List<String> requested = requestedTickers == null ? List.of() : requestedTickers;
        if (requested.size() > MAX_COMPARISONS) {
            throw new IllegalArgumentException("可比证券最多选择 3 个");
        }
        if (!requested.isEmpty() && "quick".equalsIgnoreCase(researchDepth)) {
            throw new IllegalArgumentException("可比证券研究至少需要标准研究深度");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String ticker : requested) {
            StockSubject comparison = stockCodeResolver.resolve(ticker);
            if (comparison.fullCode().equalsIgnoreCase(primarySubject.fullCode())) {
                throw new IllegalArgumentException("可比证券不能包含主证券 " + primarySubject.fullCode());
            }
            if (comparison.assetType() != primarySubject.assetType()) {
                throw new IllegalArgumentException(
                        "可比证券必须与主证券资产类型一致：" + comparison.fullCode()
                );
            }
            normalized.add(comparison.fullCode());
        }
        return List.copyOf(normalized);
    }
}
