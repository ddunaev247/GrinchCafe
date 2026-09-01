package com.grinchcafe.waiter.util;

import com.grinchcafe.waiter.model.OrderLine;

import org.json.JSONArray;
import org.json.JSONObject;

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

    public static List<Entry> build(List<OrderLine> currentLines, int openOrderNumber,
                                    int lastOrderNumber, JSONArray paidOrders) throws Exception {
        List<Entry> entries = new ArrayList<>();
        int paidLimit = currentLines == null || currentLines.isEmpty()
                ? MAX_ENTRIES : MAX_ENTRIES - 1;

        if (currentLines != null && !currentLines.isEmpty()) {
            entries.add(buildCurrentEntry(currentLines, openOrderNumber, lastOrderNumber));
        }

        if (paidOrders != null) {
            for (int i = 0; i < paidOrders.length() && entries.size() < MAX_ENTRIES; i++) {
                if (i >= paidLimit) {
                    break;
                }
                JSONObject order = paidOrders.getJSONObject(i);
                entries.add(new Entry(
                        order.optInt("orderNumber", 0),
                        false,
                        order.optLong("paidAt", 0),
                        order.optString("itemsText", ""),
                        order.optDouble("totalAmount", 0),
                        order.optInt("itemCount", 0)));
            }
        }
        return entries;
    }

    private static Entry buildCurrentEntry(List<OrderLine> lines, int openOrderNumber,
                                           int lastOrderNumber) {
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
        int orderNumber = openOrderNumber > 0 ? openOrderNumber : lastOrderNumber;
        return new Entry(orderNumber, true, 0, itemsText.toString().trim(), total, itemCount);
    }
}
