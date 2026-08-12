package com.zzy.finsight.infrastructure.persistence.mybatis.typehandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * 将 JSON 字段双向转换为事件负载 Map。
 */
public class MapJsonTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            Map<String, Object> parameter,
            JdbcType jdbcType
    ) throws SQLException {
        try {
            statement.setString(index, OBJECT_MAPPER.writeValueAsString(parameter));
        } catch (Exception exception) {
            throw new SQLException("序列化事件负载 JSON 失败", exception);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return read(resultSet.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return read(resultSet.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return read(statement.getString(columnIndex));
    }

    private Map<String, Object> read(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw new SQLException("反序列化事件负载 JSON 失败", exception);
        }
    }
}
