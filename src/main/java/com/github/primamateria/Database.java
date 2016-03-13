package com.github.primamateria;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Database {

    private final Connection connection;
    private final String INSERT_TIME = "INSERT INTO %s (time, forced) VALUES (?, ?)";
    private final String SELECT_MIN_WAKEUP_TIME = "SELECT MIN(time) FROM wakeups WHERE time >= ? AND time < ?";
    private final String SELECT_MAX_SLEEP_TIME = "SELECT MAX(time) FROM sleeps WHERE time >= ? AND time < ?";
    private final String SELECT_FORCED_WAKEUP_TIME = "SELECT time FROM wakeups WHERE time >= ? AND time < ? AND forced = TRUE";
    private final String SELECT_FORCED_SLEEP_TIME = "SELECT time FROM sleeps WHERE time >= ? AND time < ? AND forced = TRUE";
    private final String UPDATE_FORCED_TO_FALSE = "UPDATE %s SET forced = FALSE WHERE time >= ? AND time < ?";

    public Database(String dbLocation) throws SQLException {
        dbLocation = prepareDbLocation(dbLocation);
        connection = DriverManager.getConnection("jdbc:hsqldb:file:" + dbLocation + "timetrackdb", "SA", "");
        connection.setAutoCommit(false);
    }

    private String prepareDbLocation(String dbLocation) {
        if (dbLocation == null) {
            dbLocation = "db/";
        }
        if (dbLocation.charAt(dbLocation.length() - 1) != File.separatorChar) {
            dbLocation += File.separator;
        }
        return dbLocation;
    }

    public void close() throws SQLException {
        connection.commit();
        connection.close();
    }

    public void saveWakeupTime(LocalDateTime dateTime, boolean isForced) throws SQLException {
        saveTime(dateTime, "wakeups", isForced);
    }

    public void saveSleepTime(LocalDateTime dateTime, boolean isForced) throws SQLException {
        saveTime(dateTime, "sleeps", isForced);
    }

    private void saveTime(LocalDateTime dateTime, String table, boolean isForced) throws SQLException {
        if (isForced) {
            clearAnotherForcedEventsFor(dateTime, table);
        }

        Timestamp timestamp = Timestamp.valueOf(dateTime);

        final String q = String.format(INSERT_TIME, table);
        final PreparedStatement preparedStatement = connection.prepareStatement(q);
        preparedStatement.setTimestamp(1, timestamp);
        preparedStatement.setBoolean(2, isForced);
        preparedStatement.executeUpdate();
    }

    private void clearAnotherForcedEventsFor(LocalDateTime dateTime, String table) throws SQLException {
        final String q = String.format(UPDATE_FORCED_TO_FALSE, table);
        final PreparedStatement preparedStatement = getPreparedStatementWithWholeDayRange(dateTime.toLocalDate(), q);
        preparedStatement.executeUpdate();
    }

    public LocalTime getEarliestWakeupTime(LocalDate date) throws SQLException {
        return getTime(date, SELECT_MIN_WAKEUP_TIME);
    }

    public LocalTime getLatestSleepTime(LocalDate date) throws SQLException {
        return getTime(date, SELECT_MAX_SLEEP_TIME);
    }

    public LocalTime getForcedWakeupTime(LocalDate date) throws SQLException {
        return getTime(date, SELECT_FORCED_WAKEUP_TIME);
    }

    public LocalTime getForcedSleepTime(LocalDate date) throws SQLException {
        return getTime(date, SELECT_FORCED_SLEEP_TIME);
    }

    private LocalTime getTime(LocalDate date, String selectQuery) throws SQLException {
        final PreparedStatement preparedStatement = getPreparedStatementWithWholeDayRange(date, selectQuery);
        final ResultSet resultSet = preparedStatement.executeQuery();

        LocalTime resultTime = null;
        if (resultSet.next()) {
            final Timestamp resultTimestamp = resultSet.getTimestamp(1);
            if (resultTimestamp != null) {
                resultTime = resultTimestamp.toLocalDateTime().toLocalTime();
            }
        }
        return resultTime;
    }

    private PreparedStatement getPreparedStatementWithWholeDayRange(LocalDate date, String query) throws SQLException {
        LocalTime midnight = LocalTime.MIDNIGHT;
        LocalDateTime dayMidnight = LocalDateTime.of(date, midnight);
        LocalDateTime nextDayMidnight = dayMidnight.plusDays(1);

        Timestamp dayTimestamp = Timestamp.valueOf(dayMidnight);
        Timestamp nextDayTimestamp = Timestamp.valueOf(nextDayMidnight);

        final PreparedStatement preparedStatement = connection.prepareStatement(query);
        preparedStatement.setTimestamp(1, dayTimestamp);
        preparedStatement.setTimestamp(2, nextDayTimestamp);
        return preparedStatement;
    }

}
