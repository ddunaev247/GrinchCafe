package com.example.deviceinfo.util;

import com.example.deviceinfo.model.OrderHistory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ReportStats {

    private static final SimpleDateFormat DAY_KEY_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public final int orderCount;
    public final int itemCount;
    public final BigDecimal totalAmount;
    public final BigDecimal averageCheck;
    public final BigDecimal maxCheck;
    public final BigDecimal minCheck;
    public final Map<Integer, TableStats> byTable;
    public final Map<String, DayStats> byDay;

    public ReportStats(int orderCount, int itemCount, BigDecimal totalAmount, BigDecimal averageCheck,
                       BigDecimal maxCheck, BigDecimal minCheck,
                       Map<Integer, TableStats> byTable, Map<String, DayStats> byDay) {
        this.orderCount = orderCount;
        this.itemCount = itemCount;
        this.totalAmount = totalAmount;
        this.averageCheck = averageCheck;
        this.maxCheck = maxCheck;
        this.minCheck = minCheck;
        this.byTable = byTable;
        this.byDay = byDay;
    }

    public static ReportStats empty() {
        return new ReportStats(0, 0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                Collections.<Integer, TableStats>emptyMap(),
                Collections.<String, DayStats>emptyMap());
    }

    public static ReportStats fromHistory(List<OrderHistory> history) {
        if (history == null || history.isEmpty()) {
            return empty();
        }

        int itemCount = 0;
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxCheck = null;
        BigDecimal minCheck = null;
        Map<Integer, TableStats> byTable = new LinkedHashMap<>();
        Map<String, DayStats> byDay = new LinkedHashMap<>();

        for (OrderHistory entry : history) {
            itemCount += entry.getItemCount();
            BigDecimal amount = money(entry.getTotalAmount());
            total = total.add(amount);

            if (maxCheck == null || amount.compareTo(maxCheck) > 0) {
                maxCheck = amount;
            }
            if (minCheck == null || amount.compareTo(minCheck) < 0) {
                minCheck = amount;
            }

            TableStats tableStats = byTable.get(entry.getTableNumber());
            if (tableStats == null) {
                tableStats = new TableStats(entry.getTableNumber());
                byTable.put(entry.getTableNumber(), tableStats);
            }
            tableStats.addOrder(amount, entry.getItemCount());

            String dayKey = formatDayKey(entry.getPaidAt());
            DayStats dayStats = byDay.get(dayKey);
            if (dayStats == null) {
                dayStats = new DayStats(dayKey);
                byDay.put(dayKey, dayStats);
            }
            dayStats.addOrder(amount, entry.getItemCount());
        }

        BigDecimal avg = total.divide(BigDecimal.valueOf(history.size()), 2, RoundingMode.HALF_UP);
        return new ReportStats(history.size(), itemCount, total, avg, maxCheck, minCheck, byTable, byDay);
    }

    public static String formatDayKey(long paidAt) {
        return DAY_KEY_FORMAT.format(new Date(paidAt));
    }

    public static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static String formatMoney(BigDecimal value) {
        return String.format(Locale.getDefault(), "%.2f", value.doubleValue());
    }

    public static final class TableStats {
        public final int tableNumber;
        public int orderCount;
        public int itemCount;
        public BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        public TableStats(int tableNumber) {
            this.tableNumber = tableNumber;
        }

        public void addOrder(BigDecimal amount, int items) {
            orderCount++;
            itemCount += items;
            totalAmount = totalAmount.add(amount);
        }
    }

    public static final class DayStats {
        public final String dayKey;
        public int orderCount;
        public int itemCount;
        public BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        public DayStats(String dayKey) {
            this.dayKey = dayKey;
        }

        public void addOrder(BigDecimal amount, int items) {
            orderCount++;
            itemCount += items;
            totalAmount = totalAmount.add(amount);
        }
    }

    public List<DayStats> sortedDays() {
        List<DayStats> days = new ArrayList<>(byDay.values());
        Collections.sort(days, new Comparator<DayStats>() {
            @Override
            public int compare(DayStats a, DayStats b) {
                return a.dayKey.compareTo(b.dayKey);
            }
        });
        return days;
    }

    public List<TableStats> sortedTables() {
        List<TableStats> tables = new ArrayList<>(byTable.values());
        Collections.sort(tables, new Comparator<TableStats>() {
            @Override
            public int compare(TableStats a, TableStats b) {
                return a.tableNumber - b.tableNumber;
            }
        });
        return tables;
    }
}
