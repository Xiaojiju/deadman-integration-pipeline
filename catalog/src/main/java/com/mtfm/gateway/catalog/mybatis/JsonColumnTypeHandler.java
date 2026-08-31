package com.mtfm.gateway.catalog.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * 将 Java 侧 JSON 文本写入数据库 JSON/JSONB 列。
 *
 * <p>PostgreSQL 对预编译参数不会把 {@code varchar} 隐式转成 {@code jsonb}，
 * 因此写入时使用 {@link PGobject}；MySQL / H2 仍按普通字符串写入。
 */
public class JsonColumnTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        String product = ps.getConnection().getMetaData().getDatabaseProductName();
        if (product != null && product.toLowerCase(Locale.ROOT).contains("postgresql")) {
            PGobject json = new PGobject();
            json.setType("jsonb");
            json.setValue(parameter);
            ps.setObject(i, json);
            return;
        }
        ps.setString(i, parameter);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toJsonString(rs.getObject(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toJsonString(rs.getObject(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toJsonString(cs.getObject(columnIndex));
    }

    private static String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof PGobject pg) {
            return pg.getValue();
        }
        return String.valueOf(value);
    }
}
