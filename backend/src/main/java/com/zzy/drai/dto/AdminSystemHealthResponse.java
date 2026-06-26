package com.zzy.drai.dto;

import java.util.Map;

public record AdminSystemHealthResponse(
        Map<String, String> components
) {
}
