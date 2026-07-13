package com.zzy.finsight.dto.stock;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * 表示股票报告错误反馈请求。
 */
public class StockBadCaseFeedbackRequest {
    /** 反馈问题类型。 */
    @NotBlank
    @JsonProperty("feedback_type")
    private String feedbackType;

    /** 问题补充说明。 */
    private String detail;

    public String getFeedbackType() {
        return feedbackType;
    }

    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }

    public String getDetail() {
        return detail == null ? "" : detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
