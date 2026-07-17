package com.zzy.finsight.component.review;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校验报告正文中的百分比、倍数和金额声明是否能由确定性指标或冻结证据支持。
 */
@Component
public class ReportNumericFactVerifier {
    private static final BigDecimal ABSOLUTE_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal RELATIVE_TOLERANCE = new BigDecimal("0.005");
    private static final Pattern FINANCIAL_NUMBER = Pattern.compile(
            "(?<![\\d.])([+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)\\s*(%|倍|亿元|万元|元)"
    );

    /** 扫描正文并返回全部金融数字声明的支持情况。 */
    public Verification verify(
            String reportBody,
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics
    ) {
        List<NumericFact> knownFacts = knownFacts(snapshot, metrics);
        List<NumericClaim> claims = extractClaims(reportBody == null ? "" : reportBody);
        List<NumericClaim> unsupported = claims.stream()
                .filter(claim -> knownFacts.stream().noneMatch(fact -> matches(fact, claim)))
                .toList();
        return new Verification(claims.size(), claims.size() - unsupported.size(), unsupported);
    }

    /** 判断两个同口径数值是否在绝对或相对误差范围内。 */
    public boolean withinTolerance(BigDecimal expected, BigDecimal actual) {
        if (expected == null || actual == null) {
            return false;
        }
        BigDecimal diff = expected.subtract(actual).abs();
        if (diff.compareTo(ABSOLUTE_TOLERANCE) <= 0) {
            return true;
        }
        if (BigDecimal.ZERO.compareTo(expected) == 0) {
            return false;
        }
        BigDecimal relative = diff.divide(expected.abs(), MathContext.DECIMAL64);
        return relative.compareTo(RELATIVE_TOLERANCE) <= 0;
    }

    private List<NumericFact> knownFacts(
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics
    ) {
        Set<NumericFact> facts = new LinkedHashSet<>();
        List<FinancialMetricResult> safeMetrics = metrics == null ? List.of() : metrics;
        safeMetrics.stream()
                .filter(metric -> "OK".equals(metric.status()))
                .map(FinancialMetricResult::displayValue)
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> extractFacts(value, facts));
        if (snapshot != null) {
            snapshot.evidenceItems().stream()
                    .filter(FinancialEvidenceItem::effective)
                    .map(FinancialEvidenceItem::excerpt)
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(value -> extractFacts(value, facts));
        }
        return List.copyOf(facts);
    }

    private List<NumericClaim> extractClaims(String body) {
        List<NumericClaim> claims = new ArrayList<>();
        int lineNumber = 0;
        for (String line : body.lines().toList()) {
            lineNumber++;
            Matcher matcher = FINANCIAL_NUMBER.matcher(line);
            while (matcher.find()) {
                ParsedNumber parsed = parse(matcher.group(1), matcher.group(2));
                claims.add(new NumericClaim(
                        matcher.group(), parsed.baseValue(), parsed.dimension(), lineNumber, line.trim()
                ));
            }
        }
        return claims;
    }

    private void extractFacts(String text, Set<NumericFact> target) {
        Matcher matcher = FINANCIAL_NUMBER.matcher(text);
        while (matcher.find()) {
            ParsedNumber parsed = parse(matcher.group(1), matcher.group(2));
            target.add(new NumericFact(parsed.baseValue(), parsed.dimension()));
        }
    }

    private ParsedNumber parse(String number, String unit) {
        BigDecimal value = new BigDecimal(number.replace(",", ""));
        return switch (unit) {
            case "元" -> new ParsedNumber(value, "MONEY");
            case "万元" -> new ParsedNumber(value.multiply(new BigDecimal("10000")), "MONEY");
            case "亿元" -> new ParsedNumber(value.multiply(new BigDecimal("100000000")), "MONEY");
            case "%" -> new ParsedNumber(value, "PERCENT");
            case "倍" -> new ParsedNumber(value, "MULTIPLE");
            default -> throw new IllegalArgumentException("不支持的金融数字单位：" + unit);
        };
    }

    private boolean matches(NumericFact fact, NumericClaim claim) {
        return fact.dimension().equals(claim.dimension())
                && withinTolerance(fact.baseValue(), claim.baseValue());
    }

    /**
     * @param totalClaims 正文金融数字声明总数。
     * @param supportedClaims 获得确定性数据支持的声明数。
     * @param unsupportedClaims 无法从指标或冻结证据追溯的声明。
     */
    public record Verification(
            int totalClaims,
            int supportedClaims,
            List<NumericClaim> unsupportedClaims
    ) {
        public Verification {
            unsupportedClaims = unsupportedClaims == null ? List.of() : List.copyOf(unsupportedClaims);
        }

        /** 返回全部数字声明是否均有依据。 */
        public boolean passed() {
            return unsupportedClaims.isEmpty();
        }
    }

    /**
     * @param token 报告中的原始数字文本。
     * @param baseValue 按统一单位换算后的数值。
     * @param dimension 数值维度，如金额、百分比或倍数。
     * @param lineNumber 所在正文行号。
     * @param line 所在正文行文本。
     */
    public record NumericClaim(
            String token,
            BigDecimal baseValue,
            String dimension,
            int lineNumber,
            String line
    ) {
    }

    private record NumericFact(BigDecimal baseValue, String dimension) {
    }

    private record ParsedNumber(BigDecimal baseValue, String dimension) {
    }
}
