package dev.vericov.analysis.adapter.jdbc;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

final class JdbcProxySupport {
    private JdbcProxySupport() {
    }

    static RecordingDataSource dataSource() {
        return new RecordingDataSource();
    }

    static final class RecordingDataSource implements DataSource {
        private final List<SqlPlan> plans = new ArrayList<>();
        private final List<String> preparedSql = new ArrayList<>();
        private boolean autoCommit = true;
        private boolean committed;
        private boolean rolledBack;

        RecordingDataSource whenSqlContains(String fragment, StatementBehavior behavior) {
            plans.add(new SqlPlan(fragment, behavior));
            return this;
        }

        List<String> preparedSql() {
            return List.copyOf(preparedSql);
        }

        boolean autoCommit() {
            return autoCommit;
        }

        boolean committed() {
            return committed;
        }

        boolean rolledBack() {
            return rolledBack;
        }

        @Override
        public Connection getConnection() {
            InvocationHandler handler = (proxy, method, args) -> connectionMethod(method, args);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    handler);
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        private Object connectionMethod(Method method, Object[] args) {
            return switch (method.getName()) {
                case "setAutoCommit" -> {
                    autoCommit = (boolean) args[0];
                    yield null;
                }
                case "getAutoCommit" -> autoCommit;
                case "commit" -> {
                    committed = true;
                    yield null;
                }
                case "rollback" -> {
                    rolledBack = true;
                    yield null;
                }
                case "prepareStatement" -> preparedStatement((String) args[0]);
                case "close" -> null;
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement preparedStatement(String sql) {
            preparedSql.add(sql);
            StatementBehavior behavior = plans.stream()
                    .filter(plan -> sql.contains(plan.fragment()))
                    .findFirst()
                    .map(SqlPlan::behavior)
                    .orElseGet(StatementBehavior::new);
            InvocationHandler handler = new PreparedStatementHandler(behavior);
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] {PreparedStatement.class},
                    handler);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    static final class StatementBehavior {
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private final List<Map<Integer, Object>> batchParameters = new ArrayList<>();
        private SQLException executeQueryException;
        private SQLException executeException;
        private SQLException executeUpdateException;
        private SQLException executeBatchException;
        private int executeUpdateResult = 1;
        private int[] executeBatchResult = new int[0];

        StatementBehavior withRows(List<Map<String, Object>> rows) {
            this.rows.clear();
            this.rows.addAll(rows);
            return this;
        }

        StatementBehavior withExecuteQueryException(SQLException exception) {
            this.executeQueryException = exception;
            return this;
        }

        StatementBehavior withExecuteException(SQLException exception) {
            this.executeException = exception;
            return this;
        }

        StatementBehavior withExecuteUpdateException(SQLException exception) {
            this.executeUpdateException = exception;
            return this;
        }

        StatementBehavior withExecuteBatchException(SQLException exception) {
            this.executeBatchException = exception;
            return this;
        }

        StatementBehavior withExecuteUpdateResult(int result) {
            this.executeUpdateResult = result;
            return this;
        }

        List<Map<Integer, Object>> batchParameters() {
            return List.copyOf(batchParameters);
        }
    }

    private record SqlPlan(String fragment, StatementBehavior behavior) {
    }

    private static final class PreparedStatementHandler implements InvocationHandler {
        private final StatementBehavior behavior;
        private final Map<Integer, Object> parameters = new HashMap<>();

        private PreparedStatementHandler(StatementBehavior behavior) {
            this.behavior = Objects.requireNonNull(behavior, "behavior");
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "setString", "setObject", "setInt", "setLong", "setBoolean" -> {
                    parameters.put((Integer) args[0], args[1]);
                    yield null;
                }
                case "setBigDecimal" -> {
                    parameters.put((Integer) args[0], args[1]);
                    yield null;
                }
                case "setNull" -> {
                    parameters.put((Integer) args[0], new SqlNull((Integer) args[1]));
                    yield null;
                }
                case "addBatch" -> {
                    behavior.batchParameters.add(Map.copyOf(new LinkedHashMap<>(parameters)));
                    yield null;
                }
                case "executeQuery" -> {
                    if (behavior.executeQueryException != null) {
                        throw behavior.executeQueryException;
                    }
                    yield resultSet(behavior.rows);
                }
                case "executeUpdate" -> {
                    if (behavior.executeUpdateException != null) {
                        throw behavior.executeUpdateException;
                    }
                    yield behavior.executeUpdateResult;
                }
                case "executeBatch" -> {
                    if (behavior.executeBatchException != null) {
                        throw behavior.executeBatchException;
                    }
                    if (behavior.executeBatchResult.length != 0) {
                        yield behavior.executeBatchResult;
                    }
                    int size = behavior.batchParameters.isEmpty() ? 0 : behavior.batchParameters.size();
                    int[] result = new int[size];
                    java.util.Arrays.fill(result, 1);
                    yield result;
                }
                case "execute" -> {
                    if (behavior.executeException != null) {
                        throw behavior.executeException;
                    }
                    yield false;
                }
                case "close", "clearParameters" -> null;
                default -> defaultValue(method.getReturnType());
            };
        }
    }

    static final class SqlNull {
        private final int sqlType;

        SqlNull(int sqlType) {
            this.sqlType = sqlType;
        }

        int sqlType() {
            return sqlType;
        }
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        InvocationHandler handler = new ResultSetHandler(rows);
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                handler);
    }

    private static final class ResultSetHandler implements InvocationHandler {
        private final List<Map<String, Object>> rows;
        private int index = -1;
        private boolean lastWasNull;

        private ResultSetHandler(List<Map<String, Object>> rows) {
            this.rows = List.copyOf(rows);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "next" -> ++index < rows.size();
                case "getString" -> stringValue(args[0]);
                case "getObject" -> objectValue(args);
                case "getInt" -> intValue(args[0]);
                case "getLong" -> longValue(args[0]);
                case "getBoolean" -> booleanValue(args[0]);
                case "getBigDecimal" -> bigDecimalValue(args[0]);
                case "wasNull" -> lastWasNull;
                case "close" -> null;
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object rawValue(Object column) {
            Object value = rows.get(index).get(column.toString());
            lastWasNull = value == null;
            return value;
        }

        private String stringValue(Object column) {
            Object value = rawValue(column);
            return value == null ? null : value.toString();
        }

        private Object objectValue(Object[] args) {
            Object value = rawValue(args[0]);
            if (args.length == 2 && value != null) {
                return ((Class<?>) args[1]).cast(value);
            }
            return value;
        }

        private int intValue(Object column) {
            Object value = rawValue(column);
            return value == null ? 0 : ((Number) value).intValue();
        }

        private long longValue(Object column) {
            Object value = rawValue(column);
            return value == null ? 0L : ((Number) value).longValue();
        }

        private boolean booleanValue(Object column) {
            Object value = rawValue(column);
            return value != null && (Boolean) value;
        }

        private BigDecimal bigDecimalValue(Object column) {
            Object value = rawValue(column);
            if (value == null) {
                return null;
            }
            if (value instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            }
            return new BigDecimal(value.toString());
        }
    }

    static Map<String, Object> row(Object... entries) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            row.put((String) entries[index], entries[index + 1]);
        }
        return row;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }

    static boolean isSqlNull(Object value, int sqlType) {
        return value instanceof SqlNull sqlNull && sqlNull.sqlType() == sqlType;
    }

    static int varcharType() {
        return Types.VARCHAR;
    }

    static int integerType() {
        return Types.INTEGER;
    }

    static int bigintType() {
        return Types.BIGINT;
    }
}
