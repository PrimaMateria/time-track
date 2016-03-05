package com.github.primamateria;

import org.apache.commons.cli.*;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class TimeTrack {

    private static final String WAKEUP = "w";
    private static final String SLEEP = "s";
    private static final String PRINT_STATS = "p";
    private static final String DATETIME = "d";
    private static final String DATETIME_ARG = "datetime";
    private static final String DATABASE = "database";
    private static final String HELP = "h";
    private static final String UNDEFINED = "-  ";
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE dd.MM.yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter argumentDatetimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public TimeTrack(String[] args) throws ParseException, SQLException {
        AnsiConsole.systemInstall();
        CommandLineParser cliParser = new DefaultParser();
        final Options options = getOptions();
        final CommandLine commandLine = cliParser.parse(options, args);

        if (commandLine.hasOption(HELP)) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("time-track", options);
            return;
        }

        String dbLocation = null;
        if (commandLine.hasOption(DATABASE)) {
            dbLocation = commandLine.getOptionValue(DATABASE);
        }

        Database db = new Database(dbLocation);
        LocalDateTime now;
        if (commandLine.hasOption(DATETIME)) {
            final String datetimeArgument = commandLine.getOptionValue(DATETIME);
            try {
                now = LocalDateTime.parse(datetimeArgument, argumentDatetimeFormatter);
            } catch (DateTimeParseException e) {
                System.err.println("Unable to parse datetime: " + datetimeArgument + ". Please see help.");
                e.printStackTrace();
                return;
            }
        } else {
            now = LocalDateTime.now();
        }

        if (commandLine.hasOption(WAKEUP)) {
            db.saveWakeupTime(now);
            System.out.println(String.format("Recorded wake up event on %s %s", now.format(dateFormatter), now.format(timeFormatter)));
        } else if (commandLine.hasOption(SLEEP)) {
            db.saveSleepTime(now);
            System.out.println(String.format("Recorded sleep event on %s %s", now.format(dateFormatter), now.format(timeFormatter)));
        }

        if (commandLine.hasOption(PRINT_STATS)) {
            // for now just print current week stats

            System.out.println(getAnsiHeader(now));
            System.out.println("====================================================");

            Duration totalWorkDuration = Duration.ZERO;

            final int todayWeekday = Math.min(now.getDayOfWeek().getValue(), DayOfWeek.SATURDAY.getValue());
            for (int i = DayOfWeek.MONDAY.getValue(); i < todayWeekday; i++) {
                final LocalDate day = now.minusDays(todayWeekday - i).toLocalDate();

                final LocalTime earliestWakeupTime = db.getEarliestWakeupTime(day);
                final LocalTime latestSleepTime = db.getLatestSleepTime(day);

                final String formattedDay = day.format(dateFormatter);
                final String formattedStart = earliestWakeupTime == null ? UNDEFINED : earliestWakeupTime.format(timeFormatter);
                final String formattedEnd = latestSleepTime == null ? UNDEFINED : latestSleepTime.format(timeFormatter);

                String formattedDeltaMessage = "";
                if (earliestWakeupTime != null && latestSleepTime != null) {
                    Duration workDuration = Duration.between(earliestWakeupTime, latestSleepTime);
                    totalWorkDuration = totalWorkDuration.plus(workDuration);

                    formattedDeltaMessage = String.format(" %s", getFormattedDuration(workDuration));
                }

                Ansi record = getAnsiRecord(formattedDay, formattedStart, formattedEnd, formattedDeltaMessage);
                System.out.println(record);
            }

            System.out.println("----------------------------------------------------");
            System.out.println(getAnsiSummary(totalWorkDuration));

        }

        db.close();
    }

    public static void main(String[] args) throws Exception {
        new TimeTrack(args);
    }

    private Ansi getAnsiHeader(LocalDateTime now) {
        //@formatter:off
        return Ansi.ansi()
                    .a("Today is ")
                    .fg(Ansi.Color.BLUE).a(now.toLocalDate().format(dateFormatter))
                    .reset();
        //@formatter:on
    }

    private Ansi getAnsiRecord(String formattedDay, String formattedStart, String formattedEnd, String formattedDeltaMessage) {
        //@formatter:off
        return Ansi.ansi()
                        .fg(Ansi.Color.BLUE).a(String.format("%20s", formattedDay))
                        .reset().a(" from ")
                        .fg(Ansi.Color.YELLOW).a(String.format("%5s", formattedStart))
                        .reset().a(" to ")
                        .fg(Ansi.Color.YELLOW).a(String.format("%5s", formattedEnd))
                        .fg(Ansi.Color.CYAN).a(formattedDeltaMessage)
                        .reset();
        //@formatter:on
    }

    private Ansi getAnsiSummary(Duration totalWorkDuration) {
        //@formatter:off
        return Ansi.ansi()
                    .a("Total worktime: ")
                    .fg(Ansi.Color.CYAN)
                    .a(getFormattedDuration(totalWorkDuration))
                    .reset();
        //@formatter:on
    }

    private String getFormattedDuration(Duration totalWorkDuration) {
        long s = totalWorkDuration.getSeconds();
        return String.format("%dh %02dm", s / 3600, (s % 3600) / 60);
    }

    private Options getOptions() {
        Options cliOptions = new Options();
        cliOptions.addOption(WAKEUP, false, "record wake up event");
        cliOptions.addOption(SLEEP, false, "record sleep event");
        cliOptions.addOption(PRINT_STATS, false, "print stats");
        cliOptions.addOption(HELP, false, "shows help and exits");

        final Option databaseOption = Option.builder(DATABASE).hasArg().argName("location").optionalArg(false)
            .desc("database location folder. If not specified default location of 'db' folder in the execution folder will be used")
            .build();
        cliOptions.addOption(databaseOption);

        final Option datetimeOption = Option.builder(DATETIME).hasArg().argName(DATETIME_ARG).optionalArg(false).desc(
            "specific datetime in format 31.12.2016 24:59. If specified with record event option, it will record event on that datetime. "
                + "If specified with print stats option, it will print stats as looking on them on the datetime.").build();
        cliOptions.addOption(datetimeOption);
        return cliOptions;
    }

}
