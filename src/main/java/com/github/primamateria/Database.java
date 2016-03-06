package com.github.primamateria;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Database {

    private final Connection connection;
    private final String INSERT_TIME = "INSERT INTO %s (time) VALUES (?)";
    private final String SELECT_MIN_WAKEUP_TIME = "SELECT MIN(time) FROM wakeups WHERE time >= ? AND time < ?";
    private final String SELECT_MAX_SLEEP_TIME = "SELECT MAX(time) FROM sleeps WHERE time >= ? AND time < ?";

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

    public void saveWakeupTime(LocalDateTime dateTime) throws SQLException {
        saveTime(dateTime, "wakeups");
    }

    public void saveSleepTime(LocalDateTime dateTime) throws SQLException {
        saveTime(dateTime, "sleeps");
    }

    private void saveTime(LocalDateTime dateTime, String table) throws SQLException {
        Timestamp timestamp = Timestamp.valueOf(dateTime);

        final String q = String.format(INSERT_TIME, table);
        final PreparedStatement preparedStatement = connection.prepareStatement(q);
        preparedStatement.setTimestamp(1, timestamp);
        preparedStatement.executeUpdate();
    }

    public LocalTime getEarliestWakeupTime(LocalDate date) throws SQLException {
        return getTime(date, SELECT_MIN_WAKEUP_TIME);
    }

    public LocalTime getLatestSleepTime(LocalDate date) throws SQLException {
        return getTime(date, SELECT_MAX_SLEEP_TIME);
    }

    private LocalTime getTime(LocalDate date, String selectQuery) throws SQLException {
        LocalTime midnight = LocalTime.MIDNIGHT;
        LocalDateTime dayMidnight = LocalDateTime.of(date, midnight);
        LocalDateTime nextDayMidnight = dayMidnight.plusDays(1);

        Timestamp dayTimestamp = Timestamp.valueOf(dayMidnight);
        Timestamp nextDayTimestamp = Timestamp.valueOf(nextDayMidnight);

        final String q = String.format(selectQuery, dayTimestamp, nextDayTimestamp);
        final PreparedStatement preparedStatement = connection.prepareStatement(q);
        preparedStatement.setTimestamp(1, dayTimestamp);
        preparedStatement.setTimestamp(2, nextDayTimestamp);
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

}
