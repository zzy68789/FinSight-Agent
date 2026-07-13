package com.zzy.finsight.architecture;

import com.zzy.finsight.component.analysis.FinancialMetricEngine;
import com.zzy.finsight.component.marketdata.FinancialSnapshotBuilder;
import com.zzy.finsight.component.review.CitationReviewer;
import com.zzy.finsight.component.workflow.StockReportWorkflow;
import com.zzy.finsight.infrastructure.persistence.mybatis.typehandler.FinancialSnapshotJsonTypeHandler;
import com.zzy.finsight.service.AdminService;
import com.zzy.finsight.service.AuthService;
import com.zzy.finsight.service.ReportExportService;
import com.zzy.finsight.service.ReportService;
import com.zzy.finsight.service.SseService;
import com.zzy.finsight.service.StockReportService;
import com.zzy.finsight.service.TaskQueryService;
import com.zzy.finsight.service.TaskRuntimeStateService;
import com.zzy.finsight.service.impl.AdminServiceImpl;
import com.zzy.finsight.service.impl.AuthServiceImpl;
import com.zzy.finsight.service.impl.ReportExportServiceImpl;
import com.zzy.finsight.service.impl.ReportServiceImpl;
import com.zzy.finsight.service.impl.SseServiceImpl;
import com.zzy.finsight.service.impl.StockReportServiceImpl;
import com.zzy.finsight.service.impl.TaskQueryServiceImpl;
import com.zzy.finsight.service.impl.TaskRuntimeStateServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureLayeringTest {
    @Test
    void serviceContractsAreInterfacesAndImplementationsStayInImplPackage() {
        List<Class<?>> contracts = List.of(
                AdminService.class,
                AuthService.class,
                ReportExportService.class,
                ReportService.class,
                SseService.class,
                StockReportService.class,
                TaskQueryService.class,
                TaskRuntimeStateService.class
        );
        assertThat(contracts).allSatisfy(contract -> assertThat(contract.isInterface()).isTrue());

        assertThat(AdminService.class).isAssignableFrom(AdminServiceImpl.class);
        assertThat(AuthService.class).isAssignableFrom(AuthServiceImpl.class);
        assertThat(ReportExportService.class).isAssignableFrom(ReportExportServiceImpl.class);
        assertThat(ReportService.class).isAssignableFrom(ReportServiceImpl.class);
        assertThat(SseService.class).isAssignableFrom(SseServiceImpl.class);
        assertThat(StockReportService.class).isAssignableFrom(StockReportServiceImpl.class);
        assertThat(TaskQueryService.class).isAssignableFrom(TaskQueryServiceImpl.class);
        assertThat(TaskRuntimeStateService.class).isAssignableFrom(TaskRuntimeStateServiceImpl.class);
    }

    @Test
    void businessComponentsAndPersistenceAdaptersStayInDedicatedPackages() {
        assertThat(FinancialMetricEngine.class.getPackageName()).isEqualTo("com.zzy.finsight.component.analysis");
        assertThat(FinancialSnapshotBuilder.class.getPackageName()).isEqualTo("com.zzy.finsight.component.marketdata");
        assertThat(CitationReviewer.class.getPackageName()).isEqualTo("com.zzy.finsight.component.review");
        assertThat(StockReportWorkflow.class.getPackageName()).isEqualTo("com.zzy.finsight.component.workflow");
        assertThat(FinancialSnapshotJsonTypeHandler.class.getPackageName())
                .isEqualTo("com.zzy.finsight.infrastructure.persistence.mybatis.typehandler");
    }
}
