package com.zzy.finsight.component.analysis;

import com.zzy.finsight.domain.stock.StockSubject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComparisonTickerPolicyTest {
    private final StockCodeResolver resolver = new StockCodeResolver();
    private final ComparisonTickerPolicy policy = new ComparisonTickerPolicy(resolver);
    private final StockSubject primary = resolver.resolve("600519");

    @Test
    void normalizesSuffixAndDeduplicatesComparableEquities() {
        assertThat(policy.normalize(primary, List.of("000858", "000858.SZ", "600809"), "standard"))
                .containsExactly("000858.SZ", "600809.SH");
    }

    @Test
    void rejectsPrimaryCrossAssetQuickAndMoreThanThreeInputs() {
        assertThatThrownBy(() -> policy.normalize(primary, List.of("600519"), "standard"))
                .hasMessageContaining("不能包含主证券");
        assertThatThrownBy(() -> policy.normalize(primary, List.of("510300"), "standard"))
                .hasMessageContaining("资产类型一致");
        assertThatThrownBy(() -> policy.normalize(primary, List.of("000858"), "quick"))
                .hasMessageContaining("标准研究深度");
        assertThatThrownBy(() -> policy.normalize(
                primary, List.of("000858", "000568", "600809", "603369"), "deep"
        )).hasMessageContaining("最多选择 3 个");
    }
}
