package com.zzy.finsight.agent.planning;

import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.dto.agent.ResearchIntent;
import org.springframework.stereotype.Component;

/**
 * 集中校验研究意图与证券资产类型的兼容关系。
 */
@Component
public class ResearchIntentPolicy {
    /** 校验研究意图是否适用于当前资产类型，不允许不兼容请求进入任务队列。 */
    public void validate(ResearchIntent intent, StockAssetType assetType) {
        ResearchIntent normalized = intent == null ? ResearchIntent.COMPREHENSIVE : intent;
        if (!normalized.supports(assetType)) {
            throw new IllegalArgumentException(
                    "研究意图“%s”不适用于资产类型 %s".formatted(normalized.displayName(), assetType)
            );
        }
    }
}
