package com.github.winefoxbot.core.model.type; // 假设放在这个包下

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis-Plus 的 PostgreSQL JSONB 类型处理器
 * <p>
 * 用于将 Java 对象与 PGSQL 的 jsonb 类型进行映射。
 * 使用 Jackson 进行序列化和反序列化。
 * </p>
 */
@Slf4j
@MappedJdbcTypes(JdbcType.OTHER) // 将此处理器映射到 JDBC 的 OTHER 类型，因为 jsonb 是 PG 的特定类型
@MappedTypes(Object.class)       // 声明这个处理器可以处理 Java 的 Object 类型
public class PGJsonbTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // 配置 ObjectMapper：序列化时忽略 null 值的字段，增加可读性
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        // 将 Java 对象转换为 JSON 字符串，并封装为 PGobject
        PGobject jsonbObject = new PGobject();
        jsonbObject.setType("jsonb");
        try {
            jsonbObject.setValue(objectMapper.writeValueAsString(parameter));
        } catch (Exception e) {
            log.error("Failed to serialize object to JSONB for PreparedStatement.", e);
            throw new RuntimeException(e);
        }
        ps.setObject(i, jsonbObject);
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 从 ResultSet 中获取 JSON 字符串并转换为 Java 对象
        String jsonSource = rs.getString(columnName);
        return parseJson(jsonSource);
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String jsonSource = rs.getString(columnIndex);
        return parseJson(jsonSource);
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String jsonSource = cs.getString(columnIndex);
        return parseJson(jsonSource);
    }

    /**
     * 将 JSON 字符串解析为 Object 对象
     * @param json JSON 字符串
     * @return 解析后的对象，或在失败/空字符串时返回 null
     */
    private Object parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            // 使用 JsonNode 进行预处理，这可以给我们更多控制权
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return node.asText();
            }
            return objectMapper.treeToValue(node, Object.class);
        } catch (Exception e) {
            log.error("Failed to parse JSONB string to object. JSON: {}", json, e);
            // 降级策略：如果解析JSON失败（可能它就不是一个合法的JSON），
            // 作为一个备选方案，可以直接返回原始字符串。
            // 比如，如果列中存的是一个非JSON的普通文本 'r18'
            return json;
        }
    }

}
