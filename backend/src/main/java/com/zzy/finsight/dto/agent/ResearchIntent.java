package com.zzy.finsight.dto.agent;

import com.zzy.finsight.domain.stock.StockAssetType;

/**
 * 定义用户选择的研究意图；意图只约束研究重点，不固定 Agent 的工具执行顺序。
 */
public enum ResearchIntent {
    COMPREHENSIVE("综合研究", "综合核验财务表现、估值、风险与证据完整性"),
    FINANCIAL_QUALITY("财务质量", "重点核验盈利质量、现金流、成长性与异常变化"),
    VALUATION_RISK("估值风险", "重点核验估值口径、风险因素与结论不确定性"),
    ETF_TRACKING("ETF 跟踪", "重点核验基金资料、跟踪标的、净值与跟踪风险"),
    EVENT_IMPACT("事件影响", "重点核验截止日期前的公告、事件及其影响证据");

    /** 界面和错误提示使用的中文名称。 */
    private final String displayName;

    /** 提供给 Planner 的研究重点说明。 */
    private final String plannerInstruction;

    ResearchIntent(String displayName, String plannerInstruction) {
        this.displayName = displayName;
        this.plannerInstruction = plannerInstruction;
    }

    /** 返回界面和错误提示使用的中文名称。 */
    public String displayName() {
        return displayName;
    }

    /** 返回提供给 Planner 的研究重点说明。 */
    public String plannerInstruction() {
        return plannerInstruction;
    }

    /** 判断当前意图是否适用于指定资产类型。 */
    public boolean supports(StockAssetType assetType) {
        if (assetType == null) {
            return false;
        }
        return switch (this) {
            case FINANCIAL_QUALITY -> StockAssetType.EQUITY.equals(assetType);
            case ETF_TRACKING -> StockAssetType.ETF.equals(assetType);
            default -> true;
        };
    }
}
