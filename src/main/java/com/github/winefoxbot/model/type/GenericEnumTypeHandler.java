package com.github.winefoxbot.model.type;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * GenericEnumTypeHandler 泛型枚举类型处理器
 * @param <E>
 */
public class GenericEnumTypeHandler<E extends Enum<E> & BaseEnum<?>> implements TypeHandler<E> {

    private final Class<E> type;

    public GenericEnumTypeHandler(Class<E> type) {
        this.type = type;
    }

    @Override
    public void setParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
        // 如果参数是布尔类型，映射为 1 或 0
        if (parameter.getValue() instanceof Boolean) {
            ps.setInt(i, (Boolean) parameter.getValue() ? 1 : 0);
        } else {
            ps.setObject(i, parameter.getValue());
        }
    }

    @Override
    public E getResult(ResultSet rs, String columnName) throws SQLException {
        return valueOf(rs.getObject(columnName));
    }

    @Override
    public E getResult(ResultSet rs, int columnIndex) throws SQLException {
        return valueOf(rs.getObject(columnIndex));
    }

    @Override
    public E getResult(CallableStatement cs, int columnIndex) throws SQLException {
        return valueOf(cs.getObject(columnIndex));
    }

    private E valueOf(Object dbValue) {
        if (dbValue == null) return null;

        Object finalValue = dbValue;

        // 👇 如果是 Boolean，转换为对应的 int 值
        if (dbValue instanceof Boolean) {
            finalValue = (Boolean) dbValue ? 1 : 0;
        }

        for (E e : type.getEnumConstants()) {
            if (e.getValue().toString().equals(finalValue.toString())) {
                return e;
            }
        }

        throw new IllegalArgumentException("Unknown enum value: " + dbValue + " for enum " + type.getName());
    }
}
