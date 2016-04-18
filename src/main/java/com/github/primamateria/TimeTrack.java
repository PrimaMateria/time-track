package com.github.primamateria;

import org.apache.commons.cli.*;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class TimeTrack {

    private static final String OPT_WAKEUP = "w";
    private static final String OPT_SLEEP = "s";
    private static final String OPT_PRINT_STATS = "p";
    private static final String OPT_DATETIME = "d";
    private static final String OPT_DATABASE = "database";
    private static final String OPT_HELP = "h";
    private static final String OPT_FORCE = "f";

    private static final String UNDEFINED_TIME = "-  ";

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE dd.MM.yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter datetimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Duration idealWorkDuration = Duration.ZERO.plus(Duration.ofHours(8)).plus(Duration.ofMinutes(40));

    public TimeTrack(String[] args) throws ParseException, SQLException {
        AnsiConsole.systemInstall();
        CommandLineParser cliParser = new DefaultParser();
        final Options options = getOptions();
        final CommandLine commandLine = cliParser.parse(options, args);

        final int optionsCount = commandLine.getOptions().length;
        final boolean hasNoOptions = optionsCount == 0 || (optionsCount == 1 && commandLine.hasOption(OPT_DATABASE));
        if (commandLine.hasOption(OPT_HELP) || hasNoOptions) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("time-track or time-track-win.bat", options);
            return;
        }

        String dbLocation = null;
        if (commandLine.hasOption(OPT_DATABASE)) {
            dbLocation = commandLine.getOptionValue(OPT_DATABASE);
        }

        Database db = new Database(dbLocation);
        LocalDateTime now;
        if (commandLine.hasOption(OPT_DATETIME)) {
            final String datetimeArgument = commandLine.getOptionValue(OPT_DATETIME);
            now = parseDatetimeArgument(datetimeArgument);
            if (now == null) {
                System.err.println("Unable to parse datetime: " + datetimeArgument + ". Please see help.");
                return;
            }
        } else {
            now = LocalDateTime.now();
        }

        final boolean isForced = commandLine.hasOption(OPT_FORCE);
        final String eventMessage = isForced ? "forced event" : "event";
        if (commandLine.hasOption(OPT_WAKEUP)) {
            db.saveWakeupTime(now, isForced);
            System.out.println(
                String.format("Recorded wake up " + eventMessage + " on %s %s", now.format(dateFormatter), now.format(timeFormatter)));
        } else if (commandLine.hasOption(OPT_SLEEP)) {
            db.saveSleepTime(now, isForced);
            System.out.println(
                String.format("Recorded sleep " + eventMessage + " on %s %s", now.format(dateFormatter), now.format(timeFormatter)));
        }

        if (commandLine.hasOption(OPT_PRINT_STATS)) {
            // for now just print current week stats

            System.out.println(getAnsiHeader(now));
            System.out.println("====================================================");

            Duration totalWorkDuration = Duration.ZERO;

            final int maxReportedWeekday = Math.min(now.getDayOfWeek().getValue(), DayOfWeek.FRIDAY.getValue());
            for (int weekday = DayOfWeek.MONDAY.getValue(); weekday <= maxReportedWeekday; weekday++) {
                final LocalDate day = now.minusDays(now.getDayOfWeek().getValue() - weekday).toLocalDate();

                LocalTime wakeupTime = db.getForcedWakeupTime(day);
                if (wakeupTime == null) {
                    wakeupTime = db.getEarliestWakeupTime(day);
                }

                LocalTime sleepTime = db.getForcedSleepTime(day);
                if (sleepTime == null) {
                    sleepTime = db.getLatestSleepTime(day);
                }

                final String formattedDay = day.format(dateFormatter);
                final String formattedStart = wakeupTime == null ? UNDEFINED_TIME : wakeupTime.format(timeFormatter);
                final String formattedEnd = sleepTime == null ? UNDEFINED_TIME : sleepTime.format(timeFormatter);

                Duration deltaWorkDuration = null;
                Duration deltaTotalWorkDuration = null;
                String formattedDeltaMessage = "";
                if (wakeupTime != null) {
                    Duration workDuration = null;

                    if (day.isEqual(now.toLocalDate())) {
                        workDuration = Duration.between(wakeupTime, now.toLocalTime());
                    } else if (sleepTime != null) {
                        workDuration = Duration.between(wakeupTime, sleepTime);
                    }

                    if (workDuration != null) {
                        totalWorkDuration = totalWorkDuration.plus(workDuration);
                        deltaWorkDuration = workDuration.minus(idealWorkDuration);
                        deltaTotalWorkDuration = totalWorkDuration.minus(idealWorkDuration.multipliedBy(weekday));

                        formattedDeltaMessage = String.format(" %s", getFormattedDuration(workDuration));
                    }
                }

                Ansi record = getAnsiRecord(formattedDay, formattedStart, formattedEnd, formattedDeltaMessage, deltaWorkDuration,
                    deltaTotalWorkDuration);
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

    private LocalDateTime parseDatetimeArgument(String datetimeArgument) {
        LocalDateTime parsedDatetime;
        try {
            parsedDatetime = LocalDateTime.parse(datetimeArgument, datetimeFormatter);
        } catch (DateTimeParseException e) {
            try {
                final LocalTime parsedTime = LocalTime.parse(datetimeArgument, timeFormatter);
                parsedDatetime = LocalDateTime.of(LocalDate.now(), parsedTime);
            } catch (DateTimeParseException e1) {
                return null;
            }
        }
        return parsedDatetime;
    }

    private Ansi getAnsiHeader(LocalDateTime now) {
        //@formatter:off
        return Ansi.ansi()
                    .a("Today is ")
                    .fg(Ansi.Color.BLUE).a(now.toLocalDate().format(dateFormatter))
                    .reset();
        //@formatter:on
    }

    private Ansi getAnsiRecord(String formattedDay, String formattedStart, String formattedEnd, String formattedDeltaMessage,
        Duration deltaWorkDuration, Duration deltaTotalWorkDuration) {

        //@formatter:off
        Ansi record = Ansi.ansi()
                        .fg(Ansi.Color.BLUE).a(String.format("%20s", formattedDay))
                        .reset().a(" from ")
                        .fg(Ansi.Color.YELLOW).a(String.format("%5s", formattedStart))
                        .reset().a(" to ")
                        .fg(Ansi.Color.YELLOW).a(String.format("%5s", formattedEnd))
                        .fg(Ansi.Color.CYAN).a(formattedDeltaMessage)
                        .reset();
        //@formatter:on

        if (deltaTotalWorkDuration != null && deltaWorkDuration != null) {
            //@formatter:off
            record = record.reset().a(" [")
                        .a(getAnsiDeltaDuration(deltaWorkDuration))
                        .reset().a(" / ")
                        .a(getAnsiDeltaDuration(deltaTotalWorkDuration))
                        .reset().a("]");
            //@formatter:on
        }
        return record;
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

    private String getFormattedDuration(Duration duration) {
        long s = Math.abs(duration.getSeconds());
        return String.format("%dh %02dm", s / 3600, (s % 3600) / 60);
    }

    private Ansi getAnsiDeltaDuration(Duration deltaDuration) {
        if (deltaDuration == null)
            return null;

        final boolean isNegative = deltaDuration.isNegative();
        String sign = isNegative ? "-" : "+";
        String formattedDuration = getFormattedDuration(deltaDuration);

        //@formatter:off
        return Ansi.ansi()
                    .fg(isNegative ? Ansi.Color.RED : Ansi.Color.GREEN).a(sign + formattedDuration)
                    .reset();
        //@formatter:on
    }

    private Options getOptions() {
        Options cliOptions = new Options();
        cliOptions.addOption(OPT_WAKEUP, false, "record wake up event");
        cliOptions.addOption(OPT_SLEEP, false, "record sleep event");
        cliOptions.addOption(OPT_PRINT_STATS, false, "print stats");
        cliOptions.addOption(OPT_HELP, false, "shows help and exits");
        cliOptions.addOption(OPT_FORCE, false, "force event to be recorded. Can be used only with " + OPT_SLEEP + " or " + OPT_WAKEUP
            + " options. It makes sense to force when " + OPT_DATETIME
            + " option is specified. Forced event means, even if earlier wake up event or later sleep event was recorded, the datetime of forced event will be used to compute work time.");

        final Option databaseOption = Option.builder(OPT_DATABASE).hasArg().argName("location").optionalArg(false)
            .desc("database location folder. If not specified default location of 'db' folder in the execution folder will be used")
            .build();
        cliOptions.addOption(databaseOption);

        final Option datetimeOption = Option.builder(OPT_DATETIME).hasArg().argName("datetime").optionalArg(false).desc(
            "specific datetime in format 31.12.2016 24:59. When only time is specified, today will be used as date. If specified with record event option, it will record event on that datetime. "
                + "If specified with print stats option, it will print stats as looking on them on the datetime.").build();
        cliOptions.addOption(datetimeOption);
        return cliOptions;
    }

}
