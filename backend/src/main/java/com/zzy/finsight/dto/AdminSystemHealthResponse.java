package com.zzy.finsight.dto;

import java.util.Map;

/**
 * 表示管理员查看的系统组件健康状态。
 * @param components 系统组件状态。
 */
public record AdminSystemHealthResponse(
        Map<String, String> components
) {
}
