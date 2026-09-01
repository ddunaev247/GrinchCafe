package com.grinchcafe.admin.util;

import com.grinchcafe.admin.model.ReportPeriod;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Временной интервал отчёта: [fromInclusive, toExclusive). */
public final class ReportRange {

    private static final SimpleDateFormat DAY_LABEL =
            new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
    private static final SimpleDateFormat MONTH_LABEL =
            new SimpleDateFormat("LLLL yyyy", Locale.getDefault());
    private static final SimpleDateFormat DAY_TITLE =
            new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
    private static final SimpleDateFormat MONTH_TITLE =
            new SimpleDateFormat("MM.yyyy", Locale.getDefault());
    private static final SimpleDateFormat PERIOD_END =
            new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

    public final ReportPeriod period;
    public final long fromInclusive;
    public final long toExclusive;
    public final String title;
    public final String navLabel;

    private ReportRange(ReportPeriod period, long fromInclusive, long toExclusive,
                        String title, String navLabel) {
        this.period = period;
        this.fromInclusive = fromInclusive;
        this.toExclusive = toExclusive;
        this.title = title;
        this.navLabel = navLabel;
    }

    public static ReportRange forAnchor(ReportPeriod period, Calendar anchor, long firstOrderAt) {
        Calendar from = truncateToDay(anchor);
        Calendar to;
        String title;
        String navLabel;

        switch (period) {
            case MONTH:
                from.set(Calendar.DAY_OF_MONTH, 1);
                to = (Calendar) from.clone();
                to.add(Calendar.MONTH, 1);
                title = "Отчёт за " + MONTH_TITLE.format(from.getTime());
                navLabel = capitalize(MONTH_LABEL.format(from.getTime()));
                break;
            case YEAR:
                from.set(Calendar.MONTH, Calendar.JANUARY);
                from.set(Calendar.DAY_OF_MONTH, 1);
                to = (Calendar) from.clone();
                to.add(Calendar.YEAR, 1);
                title = "Отчёт за " + from.get(Calendar.YEAR) + " год";
                navLabel = String.valueOf(from.get(Calendar.YEAR));
                break;
            case ALL:
                from.setTimeInMillis(firstOrderAt > 0 ? firstOrderAt : 0);
                from = truncateToDay(from);
                to = truncateToDay(Calendar.getInstance());
                to.add(Calendar.DAY_OF_YEAR, 1);
                title = "Отчёт за всё время";
                navLabel = "Всё время";
                break;
            case DAY:
            default:
                to = (Calendar) from.clone();
                to.add(Calendar.DAY_OF_YEAR, 1);
                title = "Отчёт за " + DAY_TITLE.format(from.getTime());
                navLabel = DAY_LABEL.format(from.getTime());
                break;
        }

        return new ReportRange(period, from.getTimeInMillis(), to.getTimeInMillis(), title, navLabel);
    }

    public String fileSlug() {
        Calendar from = Calendar.getInstance();
        from.setTimeInMillis(fromInclusive);
        switch (period) {
            case MONTH:
                return new SimpleDateFormat("yyyy-MM", Locale.US).format(from.getTime());
            case YEAR:
                return String.valueOf(from.get(Calendar.YEAR));
            case ALL:
                return "vse-vremya";
            case DAY:
            default:
                return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(from.getTime());
        }
    }

    public String formatPeriodLine() {
        if (period == ReportPeriod.ALL) {
            if (fromInclusive <= 0) {
                return "Период: за всё время";
            }
            return "Период: "
                    + DAY_LABEL.format(fromInclusive)
                    + " — "
                    + PERIOD_END.format(toExclusive - 1);
        }
        if (period == ReportPeriod.DAY) {
            return "Период: " + DAY_LABEL.format(fromInclusive);
        }
        return "Период: "
                + DAY_LABEL.format(fromInclusive)
                + " — "
                + PERIOD_END.format(toExclusive - 1);
    }

    public static void shiftAnchor(Calendar anchor, ReportPeriod period, int direction) {
        switch (period) {
            case MONTH:
                anchor.add(Calendar.MONTH, direction);
                break;
            case YEAR:
                anchor.add(Calendar.YEAR, direction);
                break;
            case ALL:
                break;
            case DAY:
            default:
                anchor.add(Calendar.DAY_OF_YEAR, direction);
                break;
        }
    }

    public static Calendar truncateToDay(Calendar cal) {
        Calendar copy = (Calendar) cal.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy;
    }

    private static String capitalize(String value) {
        if (value == null || value.length() == 0) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
    }
}
