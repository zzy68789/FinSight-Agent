package com.zzy.finsight.service.impl;

import com.zzy.finsight.component.analysis.StockCodeResolver;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.reference.AShareCompanyDirectory;
import com.zzy.finsight.dto.stock.SecurityCandidateResponse;
import com.zzy.finsight.dto.stock.SecurityMatchType;
import com.zzy.finsight.dto.stock.SecurityPreviewResponse;
import com.zzy.finsight.service.SecurityQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 基于代码规则和本地证券主档实现无副作用的搜索与预解析。
 */
@Service
public class SecurityQueryServiceImpl implements SecurityQueryService {
    private static final int MAX_RESULTS = 10;
    private final StockCodeResolver resolver;
    private final AShareCompanyDirectory companyDirectory;

    public SecurityQueryServiceImpl(StockCodeResolver resolver, AShareCompanyDirectory companyDirectory) {
        this.resolver = resolver;
        this.companyDirectory = companyDirectory;
    }

    @Override
    public List<SecurityCandidateResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalized = query.trim().toUpperCase(Locale.ROOT);
        if (normalized.matches("\\d{6}(\\.(SH|SZ))?")) {
            SecurityPreviewResponse preview = preview(normalized);
            if (!preview.resolved()) {
                return List.of();
            }
            return List.of(new SecurityCandidateResponse(
                    preview.ticker(),
                    preview.fullCode(),
                    preview.companyName(),
                    preview.industry(),
                    preview.assetType(),
                    SecurityMatchType.EXACT_CODE,
                    preview.nameResolved()
            ));
        }
        return companyDirectory.search(normalized, MAX_RESULTS).stream()
                .map(profile -> candidate(profile, normalized))
                .toList();
    }

    @Override
    public SecurityPreviewResponse preview(String ticker) {
        String query = ticker == null ? "" : ticker.trim();
        try {
            StockSubject subject = resolver.resolve(query);
            boolean nameResolved = companyDirectory.findByTicker(subject.ticker()).isPresent();
            String companyName = nameResolved ? subject.companyName() : "";
            String industry = nameResolved ? subject.industry() : subject.isEtf() ? "ETF" : "";
            String message = nameResolved ? "已识别证券" : "代码受支持，但本地主档暂无证券名称";
            return new SecurityPreviewResponse(
                    query,
                    subject.ticker(),
                    subject.fullCode(),
                    companyName,
                    industry,
                    subject.assetType(),
                    true,
                    nameResolved,
                    message
            );
        } catch (IllegalArgumentException exception) {
            return new SecurityPreviewResponse(
                    query, "", "", "", "", null, false, false, exception.getMessage()
            );
        }
    }

    private SecurityCandidateResponse candidate(
            AShareCompanyDirectory.AShareCompanyProfile profile,
            String query
    ) {
        StockSubject subject = resolver.resolve(profile.ticker());
        String normalizedName = profile.companyName().toUpperCase(Locale.ROOT);
        SecurityMatchType matchType;
        if (profile.ticker().equals(query)) {
            matchType = SecurityMatchType.EXACT_CODE;
        } else if (normalizedName.equals(query)) {
            matchType = SecurityMatchType.EXACT_NAME;
        } else if (profile.ticker().startsWith(query)) {
            matchType = SecurityMatchType.CODE_PREFIX;
        } else if (normalizedName.startsWith(query)) {
            matchType = SecurityMatchType.NAME_PREFIX;
        } else {
            matchType = SecurityMatchType.NAME_CONTAINS;
        }
        return new SecurityCandidateResponse(
                subject.ticker(),
                subject.fullCode(),
                profile.companyName(),
                profile.industry(),
                subject.assetType(),
                matchType,
                true
        );
    }
}
