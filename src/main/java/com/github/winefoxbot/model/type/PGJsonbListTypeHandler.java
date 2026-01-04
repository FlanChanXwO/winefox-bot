package com.github.winefoxbot.model.type;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * MyBatis TypeHandler for mapping between a {@code List<String>} and PostgreSQL {@code jsonb} type.
 * This handler is specifically designed for PostgreSQL and is thread-safe.
 */
@Slf4j
@MappedTypes(List.class) // 声明这个 Handler 处理 Java 中的 List 类型
@MappedJdbcTypes(JdbcType.OTHER) // 声明它对应数据库中的 OTHER 类型（jsonb 通常被归为此类）
public class PGJsonbListTypeHandler extends BaseTypeHandler<List<String>> {

    private static ObjectMapper objectMapper;

    // 静态代码块，用于初始化 ObjectMapper
    static {
        // 在 Spring 环境下，更好的方式是通过依赖注入获取 ObjectMapper 实例。
        // 但在 TypeHandler 中，静态初始化是一个简单可行的方案。
        objectMapper = new ObjectMapper();
    }

    /**
     * 将 List<String> 转换为 jsonb 存入数据库
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        // 必须在方法内部创建新的 PGobject，以保证线程安全
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb"); // 明确指定为 jsonb
        try {
            // 使用 ObjectMapper 将 List<String> 序列化为 JSON 字符串
            String jsonValue = objectMapper.writeValueAsString(parameter);
            jsonObject.setValue(jsonValue);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize List<String> to JSONB.", e);
            throw new SQLException("Error while converting List to JSONB: " + e.getMessage(), e);
        }
        ps.setObject(i, jsonObject);
    }

    /**
     * 从数据库中读取 jsonb 并转换为 List<String>
     */
    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }

    /**
     * 辅助方法，用于将 JSON 字符串解析为 List<String>
     */
    private List<String> parseJson(String json) throws SQLException {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList(); // 或返回 null，取决于业务需求
        }
        try {
            // 使用 TypeReference 来正确地反序列化泛型列表
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            log.error("Failed to parse JSONB to List<String>. JSON: {}", json, e);
            throw new SQLException("Error while parsing JSONB to List: " + e.getMessage(), e);
        }
    }
}
