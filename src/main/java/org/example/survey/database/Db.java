package org.example.survey.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class Db {

    @FunctionalInterface
    public interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    public interface Mapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    public interface TxWork<T> {
        T run(Connection conn) throws SQLException;
    }

    public static final Binder NONE = ps -> { };

    private Db() {
    }

    public static <T> List<T> list(String sql, Binder binder, Mapper<T> mapper) {
        try (Connection conn = DatabaseConnect.getConnection()) {
            return list(conn, sql, binder, mapper);
        } catch (SQLException e) {
            throw new DatabaseException("Query failed: " + sql, e);
        }
    }

    public static <T> List<T> list(Connection conn, String sql, Binder binder, Mapper<T> mapper) throws SQLException {
        List<T> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
            }
        }
        return result;
    }

    public static <T> T one(String sql, Binder binder, Mapper<T> mapper) {
        List<T> rows = list(sql, binder, mapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static <T> T one(Connection conn, String sql, Binder binder, Mapper<T> mapper) throws SQLException {
        List<T> rows = list(conn, sql, binder, mapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static long count(String sql, Binder binder) {
        Long value = one(sql, binder, rs -> rs.getLong(1));
        return value == null ? 0 : value;
    }

    public static int update(String sql, Binder binder) {
        try (Connection conn = DatabaseConnect.getConnection()) {
            return update(conn, sql, binder);
        } catch (SQLException e) {
            throw new DatabaseException("Update failed: " + sql, e);
        }
    }

    public static int update(Connection conn, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate();
        }
    }

    public static int insert(String sql, Binder binder) {
        try (Connection conn = DatabaseConnect.getConnection()) {
            return insert(conn, sql, binder);
        } catch (SQLException e) {
            throw new DatabaseException("Insert failed: " + sql, e);
        }
    }

    public static int insert(Connection conn, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(ps);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt("id");
                }
            }
        }
        return 0;
    }

    public static <T> T inTransaction(TxWork<T> work) {
        try (Connection conn = DatabaseConnect.getConnection()) {
            boolean previous = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previous);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Transaction failed", e);
        }
    }

    public static boolean isUniqueViolation(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    public static java.time.LocalDateTime time(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    public static java.sql.Timestamp timestamp(java.time.LocalDateTime value) {
        return value == null ? null : java.sql.Timestamp.valueOf(value);
    }
}
