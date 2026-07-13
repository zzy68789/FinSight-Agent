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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureLayeringTest {
    private static final Pattern JAVADOC_PATTERN = Pattern.compile("/\\*\\*(.*?)\\*/", Pattern.DOTALL);
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\p{IsHan}]");

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

    @Test
    void everyMainJavaFileHasChineseJavadoc() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> missingComments = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !containsChineseJavadoc(path))
                    .toList();

            assertThat(missingComments).isEmpty();
        }
    }

    /** 判断源码中是否存在中文 Javadoc。 */
    private boolean containsChineseJavadoc(Path path) {
        try {
            Matcher matcher = JAVADOC_PATTERN.matcher(Files.readString(path, StandardCharsets.UTF_8));
            while (matcher.find()) {
                if (CHINESE_PATTERN.matcher(matcher.group(1)).find()) {
                    return true;
                }
            }
            return false;
        } catch (IOException exception) {
            throw new IllegalStateException("读取源码失败：" + path, exception);
        }
    }
}
