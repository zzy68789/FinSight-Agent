package com.zzy.finsight.service;

import com.zzy.finsight.dto.stock.SecurityCandidateResponse;
import com.zzy.finsight.dto.stock.SecurityPreviewResponse;

import java.util.List;

/**
 * 定义证券代码或名称搜索与只读预解析能力。
 */
public interface SecurityQueryService {
    /** 搜索系统当前能够确认的证券候选，最多返回十条。 */
    List<SecurityCandidateResponse> search(String query);

    /** 预解析证券代码，不创建任务，也不修改 AgentState。 */
    SecurityPreviewResponse preview(String ticker);
}
