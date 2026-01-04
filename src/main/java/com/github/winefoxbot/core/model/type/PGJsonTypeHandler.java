package com.github.winefoxbot.core.model.type;

import cn.hutool.json.JSONUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PGJsonTypeHandler:处理Object对象类型与postgresql中JSON类型之间的转换
 * @param <T>
 */
@SuppressWarnings("unchecked")
@MappedTypes(value = {Object.class})
public class PGJsonTypeHandler<T extends Object> extends BaseTypeHandler<T> {
    private static final PGobject pgObject = new PGobject();

    @Override
    public void setNonNullParameter(PreparedStatement preparedStatement, int i, T parameter, JdbcType jdbcType) throws SQLException {
        if (preparedStatement != null) {
            pgObject.setType("json");
            pgObject.setValue(JSONUtil.toJsonStr(parameter));
            preparedStatement.setObject(i, pgObject);
        }
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return (T) JSONUtil.parse(rs.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return (T) JSONUtil.parse(rs.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return (T) JSONUtil.parse(cs.getString(columnIndex));
    }
}