package com.zzy.finsight.dto;

import java.util.List;

/**
 * 封装分页查询结果。
 * @param items 当前页数据列表。
 * @param page 当前页码。
 * @param size 每页数据量。
 * @param total 数据总量。
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}
