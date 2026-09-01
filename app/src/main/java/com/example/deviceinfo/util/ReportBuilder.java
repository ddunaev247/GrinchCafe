package com.example.deviceinfo.util;

import com.example.deviceinfo.model.OrderHistory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ReportBuilder {

    private static final SimpleDateFormat DATE_TIME_FORMAT =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    private static final SimpleDateFormat DAY_DISPLAY_FORMAT =
            new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

    private ReportBuilder() {
    }

    public static String formatDayKey(long paidAt) {
        return ReportStats.formatDayKey(paidAt);
    }

    public static String formatDayDisplay(String dayKey) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayKey);
            if (date != null) {
                return DAY_DISPLAY_FORMAT.format(date);
            }
        } catch (Exception ignored) {
        }
        return dayKey;
    }

    public static String buildReport(ReportRange range, List<OrderHistory> history) {
        ReportStats stats = ReportStats.fromHistory(history);
        StringBuilder sb = new StringBuilder();
        sb.append(range.title.toUpperCase(Locale.getDefault())).append("\n");
        sb.append(range.formatPeriodLine()).append("\n\n");

        appendSummary(sb, stats);

        if (stats.orderCount > 0 && stats.byTable.size() > 0) {
            sb.append("\n=== ПО СТОЛИКАМ ===\n");
            for (ReportStats.TableStats table : stats.sortedTables()) {
                sb.append("Стол №").append(table.tableNumber)
                        .append(": ").append(table.orderCount).append(" заказ(ов), ")
                        .append(table.itemCount).append(" поз., ")
                        .append(ReportStats.formatMoney(table.totalAmount)).append(" руб.\n");
            }
        }

        if (stats.orderCount > 0 && stats.byDay.size() > 1) {
            sb.append("\n=== ПО ДНЯМ ===\n");
            for (ReportStats.DayStats day : stats.sortedDays()) {
                sb.append(formatDayDisplay(day.dayKey))
                        .append(": ").append(day.orderCount).append(" заказ(ов), ")
                        .append(day.itemCount).append(" поз., ")
                        .append(ReportStats.formatMoney(day.totalAmount)).append(" руб.\n");
            }
        }

        sb.append("\n=== ПОДРОБНО ===\n");
        if (history.isEmpty()) {
            sb.append("Нет оплаченных заказов за выбранный период.\n");
        } else {
            int index = 1;
            for (OrderHistory entry : history) {
                appendOrderDetail(sb, index++, entry);
            }
        }

        sb.append("\n--- ИТОГО ---\n");
        appendSummary(sb, stats);
        return sb.toString();
    }

    private static void appendSummary(StringBuilder sb, ReportStats stats) {
        sb.append("=== СВОДКА ===\n");
        sb.append("Заказов: ").append(stats.orderCount).append("\n");
        sb.append("Позиций: ").append(stats.itemCount).append("\n");
        sb.append("Выручка: ").append(ReportStats.formatMoney(stats.totalAmount)).append(" руб.\n");
        if (stats.orderCount > 0) {
            sb.append("Средний чек: ").append(ReportStats.formatMoney(stats.averageCheck)).append(" руб.\n");
            if (stats.orderCount > 1) {
                sb.append("Мин. чек: ").append(ReportStats.formatMoney(stats.minCheck)).append(" руб.\n");
                sb.append("Макс. чек: ").append(ReportStats.formatMoney(stats.maxCheck)).append(" руб.\n");
            }
        }
    }

    private static void appendOrderDetail(StringBuilder sb, int index, OrderHistory entry) {
        sb.append("\n[").append(index).append("] ");
        sb.append(DATE_TIME_FORMAT.format(new Date(entry.getPaidAt())));
        sb.append(" | Стол №").append(entry.getTableNumber());
        if (entry.getOrderNumber() > 0) {
            sb.append(" | Заказ №").append(entry.getOrderNumber());
        }
        sb.append(" | ").append(ReportStats.formatMoney(ReportStats.money(entry.getTotalAmount())));
        sb.append(" руб. | ").append(entry.getItemCount()).append(" поз.\n");
        if (entry.getItemsText() != null && entry.getItemsText().trim().length() > 0) {
            sb.append(entry.getItemsText().trim()).append("\n");
        }
    }

    /** @deprecated Используйте {@link ReportStats#fromHistory(List)} */
    @Deprecated
    public static double calculateTotal(List<OrderHistory> history) {
        return ReportStats.fromHistory(history).totalAmount.doubleValue();
    }

    /** @deprecated Используйте {@link ReportStats#fromHistory(List)} */
    @Deprecated
    public static int calculateItemCount(List<OrderHistory> history) {
        return ReportStats.fromHistory(history).itemCount;
    }
}
