package com.zzy.finsight.config;

import com.zzy.finsight.agent.memory.EvidenceMemory;
import com.zzy.finsight.agent.tool.FinancialProviderResearchTool;
import com.zzy.finsight.agent.tool.ResearchTool;
import com.zzy.finsight.infrastructure.provider.AShareMasterDataProvider;
import com.zzy.finsight.infrastructure.provider.PublicMarketDataProvider;
import com.zzy.finsight.infrastructure.provider.TushareMarketDataProvider;
import com.zzy.finsight.infrastructure.provider.UploadedReportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册现有金融 Provider 对应的白名单 Research Tool。
 */
@Configuration
public class ResearchAgentToolConfig {

    @Bean
    ResearchTool getCompanyProfileTool(AShareMasterDataProvider provider, EvidenceMemory memory) {
        return new FinancialProviderResearchTool(
                "get_company_profile",
                "获取证券基础资料、公司名称、行业和资产类型证据。",
                provider,
                memory
        );
    }

    @Bean
    ResearchTool getFinancialStatementsTool(TushareMarketDataProvider provider, EvidenceMemory memory) {
        return new FinancialProviderResearchTool(
                "get_financial_statements",
                "获取 TuShare 财务报表、估值和 ETF 深度数据；适合财务、估值和基金问题。",
                provider,
                memory
        );
    }

    @Bean
    ResearchTool getMarketSnapshotTool(PublicMarketDataProvider provider, EvidenceMemory memory) {
        return new FinancialProviderResearchTool(
                "get_market_snapshot",
                "获取公开行情、市场状态和新闻摘要证据；适合价格、波动、估值和事件问题。",
                provider,
                memory
        );
    }

    @Bean
    ResearchTool retrieveUploadedReportsTool(UploadedReportProvider provider, EvidenceMemory memory) {
        return new FinancialProviderResearchTool(
                "retrieve_uploaded_reports",
                "从当前用户上传的年报、季报和研究材料中检索与问题相关的证据。",
                provider,
                memory
        );
    }
}
