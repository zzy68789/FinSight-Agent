package com.zzy.finsight.agent.tool;

import com.zzy.finsight.component.analysis.StockCodeResolver;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 将用户输入解析为受支持的 A 股或 ETF 研究主体。
 */
@Component
public class ResolveSecurityTool implements ResearchTool {
    private final StockCodeResolver resolver;

    public ResolveSecurityTool(StockCodeResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public String name() {
        return "resolve_security";
    }

    @Override
    public String description() {
        return "解析并标准化 A 股或 ETF 代码；其他金融工具执行前必须先调用。";
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        StockSubject subject = resolver.resolve(context.request().getTicker());
        context.state().setSubject(subject);
        if (context.state().getSnapshot() == null) {
            context.state().setSnapshot(new FinancialSnapshot(
                    subject,
                    context.request().getAsOfDate().toString(),
                    context.request().getSearchMode(),
                    List.of(),
                    LocalDateTime.now()
            ));
        }
        return ToolResult.success(
                "已解析证券 " + subject.fullCode(),
                Map.of("subject", subject, "disclaimer", "仅作研究辅助，不构成投资建议")
        );
    }
}
