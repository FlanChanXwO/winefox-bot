package com.github.winefoxbot.core.model.type;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-12-21:09
 */
public class StringListTypeHandler extends BaseTypeHandler<List<String>> {

    // 将 List<String> 转换为以逗号分隔的字符串
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        if (parameter != null && !parameter.isEmpty()) {
            // 用逗号连接 List<String>
            String result = String.join(",", parameter);
            ps.setString(i, result);
        } else {
            ps.setString(i, null);  // 如果 List 是空的或为 null，设置为 null
        }
    }

    // 从数据库的字符串中读取数据，并转换为 List<String>
    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String result = rs.getString(columnName);
        if (result != null) {
            return Arrays.asList(result.split(","));
        }
        return null;
    }

    // 从数据库的字符串中读取数据，并转换为 List<String>
    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String result = rs.getString(columnIndex);
        if (result != null) {
            return Arrays.asList(result.split(","));
        }
        return null;
    }

    // 从 CallableStatement 中获取数据，并转换为 List<String>
    @Override
    public List<String> getNullableResult(java.sql.CallableStatement cs, int columnIndex) throws SQLException {
        String result = cs.getString(columnIndex);
        if (result != null) {
            return Arrays.asList(result.split(","));
        }
        return null;
    }
}
