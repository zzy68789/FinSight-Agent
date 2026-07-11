package com.zzy.finsight.dto;

import java.util.Map;

public record AdminSystemHealthResponse(
        Map<String, String> components
) {
}
