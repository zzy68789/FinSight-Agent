package com.zzy.finsight.domain;

/**
 * 表示可被同用户复用的已通过报告。
 * @param id 主键标识。
 * @param content 正文内容。
 * @param reviewStatus 报告审查状态。
 */
public record ReusableReportRecord(long id, String content, String reviewStatus) {
}
