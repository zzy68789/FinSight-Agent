package com.zzy.finsight.dto.stock;

/**
 * 定义证券搜索候选与用户输入的匹配方式。
 */
public enum SecurityMatchType {
    EXACT_CODE,
    EXACT_NAME,
    CODE_PREFIX,
    NAME_PREFIX,
    NAME_CONTAINS
}
