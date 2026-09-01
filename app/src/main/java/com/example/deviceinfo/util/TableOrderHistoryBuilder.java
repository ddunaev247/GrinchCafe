package com.example.deviceinfo.util;

import com.example.deviceinfo.db.DatabaseHelper;
import com.example.deviceinfo.model.OrderHistory;
import com.example.deviceinfo.model.OrderLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Последние заказы столика для экрана «История». */
public final class TableOrderHistoryBuilder {

    public static final int MAX_ENTRIES = 5;

    public static final class Entry {
        public final int orderNumber;
        public final boolean current;
        public final long paidAtMs;
        public final String itemsText;
        public final double totalAmount;
        public final int itemCount;

        Entry(int orderNumber, boolean current, long paidAtMs,
              String itemsText, double totalAmount, int itemCount) {
            this.orderNumber = orderNumber;
            this.current = current;
            this.paidAtMs = paidAtMs;
            this.itemsText = itemsText;
            this.totalAmount = totalAmount;
            this.itemCount = itemCount;
        }
    }

    private TableOrderHistoryBuilder() {
    }

    public static List<Entry> build(DatabaseHelper db, long tableId, int tableNumber,
                                    List<OrderLine> currentLines, int openOrderNumber,
                                    int lastOrderNumber) {
        List<Entry> entries = new ArrayList<>();
        int paidLimit = currentLines == null || currentLines.isEmpty()
                ? MAX_ENTRIES : MAX_ENTRIES - 1;

        if (currentLines != null && !currentLines.isEmpty()) {
            entries.add(buildCurrentEntry(db, tableId, currentLines, openOrderNumber, lastOrderNumber));
        }

        for (OrderHistory history : db.getRecentPaidOrdersForTable(tableNumber, paidLimit)) {
            entries.add(new Entry(
                    history.getOrderNumber(),
                    false,
                    history.getPaidAt(),
                    history.getItemsText(),
                    history.getTotalAmount(),
                    history.getItemCount()));
        }
        return entries;
    }

    private static Entry buildCurrentEntry(DatabaseHelper db, long tableId, List<OrderLine> lines,
                                           int openOrderNumber, int lastOrderNumber) {
        double total = 0;
        int itemCount = 0;
        StringBuilder itemsText = new StringBuilder();
        for (OrderLine line : lines) {
            total += line.getLineTotal();
            itemCount += line.getCount();
            itemsText.append(line.getName()).append(" x").append(line.getCount())
                    .append(" = ")
                    .append(String.format(Locale.getDefault(), "%.2f", line.getLineTotal()))
                    .append('\n');
        }
        int orderNumber = PrintSessionHelper.resolveOrderNumberForPayment(db, tableId, lastOrderNumber);
        if (orderNumber <= 0) {
            orderNumber = openOrderNumber > 0 ? openOrderNumber : lastOrderNumber;
        }
        return new Entry(orderNumber, true, 0, itemsText.toString().trim(), total, itemCount);
    }
}
