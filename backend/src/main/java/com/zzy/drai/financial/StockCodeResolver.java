package com.zzy.drai.financial;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StockCodeResolver {
    private static final Pattern EXPLICIT_CODE = Pattern.compile("^(\\d{6})\\.(SH|SZ)$", Pattern.CASE_INSENSITIVE);
    private final AShareCompanyDirectory companyDirectory;

    public StockCodeResolver() {
        this(new AShareCompanyDirectory());
    }

    public StockCodeResolver(AShareCompanyDirectory companyDirectory) {
        this.companyDirectory = companyDirectory;
    }

    public StockSubject resolve(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        Matcher explicit = EXPLICIT_CODE.matcher(normalized);
        if (explicit.matches()) {
            String ticker = explicit.group(1);
            String exchange = explicit.group(2).toUpperCase(Locale.ROOT);
            return subject(ticker, exchange, assetType(ticker, exchange));
        }
        if (!normalized.matches("\\d{6}")) {
            throw new IllegalArgumentException("股票代码格式不正确，仅支持 A 股 6 位代码或 .SH/.SZ 后缀");
        }
        if (normalized.startsWith("6")) {
            return subject(normalized, "SH", StockAssetType.EQUITY);
        }
        if (normalized.startsWith("5")) {
            return subject(normalized, "SH", StockAssetType.ETF);
        }
        if (normalized.startsWith("0") || normalized.startsWith("2") || normalized.startsWith("3")) {
            return subject(normalized, "SZ", StockAssetType.EQUITY);
        }
        if (normalized.startsWith("15") || normalized.startsWith("16") || normalized.startsWith("18")) {
            return subject(normalized, "SZ", StockAssetType.ETF);
        }
        throw new IllegalArgumentException("当前仅支持沪深 A 股普通股票代码和常见 ETF 代码（沪市 6xxxxx/5xxxxx，深市 0/2/3xxxxx/15xxxx/16xxxx/18xxxx）；"
                + normalized + " 这类代码暂不支持 B 股、债券或北交所标的。");
    }

    private StockAssetType assetType(String ticker, String exchange) {
        if ("SH".equals(exchange) && ticker.startsWith("5")) {
            return StockAssetType.ETF;
        }
        if ("SZ".equals(exchange) && (ticker.startsWith("15") || ticker.startsWith("16") || ticker.startsWith("18"))) {
            return StockAssetType.ETF;
        }
        if (ticker.startsWith("6")
                || ticker.startsWith("0")
                || ticker.startsWith("2")
                || ticker.startsWith("3")) {
            return StockAssetType.EQUITY;
        }
        throw new IllegalArgumentException("当前仅支持沪深 A 股普通股票代码和常见 ETF 代码（沪市 6xxxxx/5xxxxx，深市 0/2/3xxxxx/15xxxx/16xxxx/18xxxx）；"
                + ticker + "." + exchange + " 这类代码暂不支持 B 股、债券或北交所标的。");
    }

    private StockSubject subject(String ticker, String exchange, StockAssetType assetType) {
        if (StockAssetType.ETF.equals(assetType)) {
            return new StockSubject(ticker, exchange, ticker + "." + exchange, "待识别ETF", "ETF", StockAssetType.ETF);
        }
        return companyDirectory.findByTicker(ticker)
                .map(profile -> new StockSubject(ticker, exchange, ticker + "." + exchange, profile.companyName(), profile.industry(), StockAssetType.EQUITY))
                .orElseGet(() -> new StockSubject(ticker, exchange, ticker + "." + exchange, "待识别上市公司", "待识别行业"));
    }
}
