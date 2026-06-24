package com.zzy.drai.config;

import com.zzy.drai.agent.node.RouterNode;
import com.zzy.drai.service.ReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {
    @Bean
    RouterNode routerNode(ReportService reportService) {
        return new RouterNode(reportService::findLatestByThread);
    }
}
