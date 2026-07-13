package com.zzy.finsight.config;

import com.zzy.finsight.domain.stock.metric.MetricDefinitionCatalog;
import com.zzy.finsight.domain.stock.reference.AShareCompanyDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册金融领域目录等无状态领域对象。
 */
@Configuration
public class FinancialDomainConfig {
    @Bean
    public MetricDefinitionCatalog metricDefinitionCatalog() {
        return new MetricDefinitionCatalog();
    }

    @Bean
    public AShareCompanyDirectory aShareCompanyDirectory() {
        return new AShareCompanyDirectory();
    }
}
