package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.component.analysis.StockCodeResolver;
import com.zzy.finsight.domain.stock.StockSubject;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将用户输入解析为受支持的 A 股或 ETF 研究主体。
 */
@Component
public class ResolveSecurityTool implements ResearchTool<NoToolArguments> {
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
    public ToolDefinition definition() {
        return ToolDefinition.readOnly(
                "resolve_security",
                "解析并标准化 A 股或 ETF 代码；其他金融工具执行前必须先调用。",
                Map.of("subject", "StockSubject", "disclaimer", "string")
        );
    }

    @Override
    public NoToolArguments decode(Map<String, Object> arguments, ObjectMapper objectMapper) {
        return ToolDecoders.noArguments(arguments);
    }

    @Override
    public ToolResult execute(ToolContext context, NoToolArguments arguments) {
        StockSubject subject = resolver.resolve(context.request().ticker());
        return ToolResult.success(
                "已解析证券 " + subject.fullCode(),
                new ToolPayload.Security(subject, "仅作研究辅助，不构成投资建议")
        );
    }
}
