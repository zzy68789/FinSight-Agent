package com.zzy.finsight.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 提供数据库连通性检查。
 */
@Mapper
public interface SystemMapper {
    int ping();
}
