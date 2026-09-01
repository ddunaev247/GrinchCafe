package com.example.deviceinfo.util;

import android.content.Context;

import com.example.deviceinfo.db.DatabaseHelper;
import com.example.deviceinfo.model.LunchComplex;
import com.example.deviceinfo.model.OrderLine;

import java.util.ArrayList;
import java.util.List;

/**
 * Подготовка печати.
 * «Печать» — рабочий формат (как кухня) на оба принтера: старые / +++ / новые.
 * «Полный чек» — гостевой чек только на бар, без «+++».
 */
public final class PrintSessionHelper {

    public static final class PrintPlan {
        public final long tableId;
        public final int orderNumber;
        public final int receiptVersion;
        public final long eventTimeMs;
        /** Гостевой чек бара (полный чек). */
        public final String barReceipt;
        /** Рабочий чек бара в формате кухни (кнопка «Печать»). */
        public final ReceiptFormatter.KitchenReceipt barKitchenReceipt;
        public final ReceiptFormatter.KitchenReceipt kitchenReceipt;
        public final List<OrderLine> tableLines;

        PrintPlan(long tableId, int orderNumber, int receiptVersion, long eventTimeMs,
                  String barReceipt, ReceiptFormatter.KitchenReceipt barKitchenReceipt,
                  ReceiptFormatter.KitchenReceipt kitchenReceipt,
                  List<OrderLine> tableLines) {
            this.tableId = tableId;
            this.orderNumber = orderNumber;
            this.receiptVersion = receiptVersion;
            this.eventTimeMs = eventTimeMs;
            this.barReceipt = barReceipt;
            this.barKitchenReceipt = barKitchenReceipt;
            this.kitchenReceipt = kitchenReceipt;
            this.tableLines = tableLines;
        }

        public boolean isEmpty() {
            return !hasGuestBarPrint() && !hasBarKitchenPrint() && !hasKitchenPrint();
        }

        public boolean hasGuestBarPrint() {
            return barReceipt != null && barReceipt.length() > 0;
        }

        public boolean hasBarKitchenPrint() {
            return barKitchenReceipt != null;
        }

        public boolean hasKitchenPrint() {
            return kitchenReceipt != null;
        }
    }

    private PrintSessionHelper() {
    }

    public static PrintPlan buildPlan(Context context, DatabaseHelper db, long tableId, int tableNumber) {
        List<OrderLine> tableLines = db.getOrderLines(tableId);
        List<OrderLine> deltas = collectDeltaLines(tableLines);
        if (deltas.isEmpty()) {
            return null;
        }

        int orderNumber = db.getOpenOrderNumber(tableId);
        if (orderNumber == 0) {
            orderNumber = db.getNextOrderNumber();
            db.setOpenOrderNumber(tableId, orderNumber);
        }

        int receiptVersion = db.getPrintVersion(tableId) + 1;
        long eventTimeMs = System.currentTimeMillis();

        List<OrderLine> printed = collectPrintedLines(tableLines);
        List<OrderLine> printedSorted = CategorySorter.sortForPrint(printed);
        List<OrderLine> deltaSorted = CategorySorter.sortForPrint(deltas);

        List<OrderLine> barOld = filterForReceipt(
                context, ReceiptCategoryConfig.Target.BAR, printedSorted);
        List<OrderLine> barNew = filterForReceipt(
                context, ReceiptCategoryConfig.Target.BAR, deltaSorted);
        List<OrderLine> kitchenOld = filterForReceipt(
                context, ReceiptCategoryConfig.Target.KITCHEN, printedSorted);
        List<OrderLine> kitchenNew = filterForReceipt(
                context, ReceiptCategoryConfig.Target.KITCHEN, deltaSorted);

        ReceiptFormatter.KitchenReceipt barKitchenReceipt =
                (barOld.isEmpty() && barNew.isEmpty())
                        ? null
                        : ReceiptFormatter.formatKitchenStyleReceipt(
                                tableNumber, orderNumber, barOld, barNew,
                                eventTimeMs, receiptVersion);
        ReceiptFormatter.KitchenReceipt kitchenReceipt =
                (kitchenOld.isEmpty() && kitchenNew.isEmpty())
                        ? null
                        : ReceiptFormatter.formatKitchenStyleReceipt(
                                tableNumber, orderNumber, kitchenOld, kitchenNew,
                                eventTimeMs, receiptVersion);

        if (barKitchenReceipt == null && kitchenReceipt == null) {
            return null;
        }

        return new PrintPlan(tableId, orderNumber, receiptVersion, eventTimeMs,
                null, barKitchenReceipt, kitchenReceipt, tableLines);
    }

    /**
     * Полный гостевой чек на бар: все текущие позиции, без «+++», без кухни
     * и без пометки printedCount (это копия, а не допечатка).
     */
    public static PrintPlan buildFullBarPlan(Context context, DatabaseHelper db,
                                            long tableId, int tableNumber) {
        List<OrderLine> tableLines = db.getOrderLines(tableId);
        if (tableLines.isEmpty()) {
            return null;
        }

        int orderNumber = db.getOpenOrderNumber(tableId);
        if (orderNumber == 0) {
            orderNumber = db.getNextOrderNumber();
            db.setOpenOrderNumber(tableId, orderNumber);
        }

        int storedVersion = db.getPrintVersion(tableId);
        int receiptVersion = storedVersion > 0 ? storedVersion : 1;
        long eventTimeMs = System.currentTimeMillis();

        List<OrderLine> allSorted = CategorySorter.sortForPrint(tableLines);
        String barReceipt = ReceiptFormatter.formatBarReceipt(
                tableNumber, orderNumber, allSorted, false, eventTimeMs, receiptVersion);
        if (barReceipt == null || barReceipt.length() == 0) {
            return null;
        }

        return new PrintPlan(tableId, orderNumber, receiptVersion, eventTimeMs,
                barReceipt, null, null, tableLines);
    }

    private static List<OrderLine> filterForReceipt(Context context, ReceiptCategoryConfig.Target target,
                                                    List<OrderLine> lines) {
        List<OrderLine> filtered = new ArrayList<>();
        for (OrderLine line : lines) {
            if (LunchComplex.isComboLine(line)
                    || ReceiptCategoryConfig.isEnabled(context, target, line.getCategory())) {
                filtered.add(line);
            }
        }
        return filtered;
    }

    public static void markPrintDelivered(DatabaseHelper db, PrintPlan plan) {
        if (plan == null) {
            return;
        }
        markPrinted(db, plan.tableLines);
        db.setPrintVersion(plan.tableId, plan.receiptVersion);
    }

    public static void markPrinted(DatabaseHelper db, List<OrderLine> lines) {
        for (OrderLine line : lines) {
            if (line.getPrintedCount() < line.getCount()) {
                line.setPrintedCount(line.getCount());
                db.updateOrderLine(line);
            }
        }
    }

    public static void clearSession(DatabaseHelper db, long tableId) {
        db.setOpenOrderNumber(tableId, 0);
        db.setPrintVersion(tableId, 0);
    }

    public static int resolveOrderNumberForPayment(DatabaseHelper db, long tableId, int fallback) {
        int open = db.getOpenOrderNumber(tableId);
        return open > 0 ? open : fallback;
    }

    public static void preservePrintedCounts(List<OrderLine> existing, List<OrderLine> incoming) {
        java.util.HashMap<String, Integer> printedByKey = new java.util.HashMap<>();
        for (OrderLine old : existing) {
            printedByKey.put(lineKey(old), old.getPrintedCount());
        }
        for (OrderLine line : incoming) {
            Integer printed = printedByKey.get(lineKey(line));
            if (printed != null) {
                line.setPrintedCount(Math.min(printed, line.getCount()));
            } else {
                line.setPrintedCount(0);
            }
        }
    }

    private static List<OrderLine> collectPrintedLines(List<OrderLine> lines) {
        List<OrderLine> printed = new ArrayList<>();
        for (OrderLine line : lines) {
            if (line.getPrintedCount() > 0) {
                printed.add(copyWithCount(line, line.getPrintedCount()));
            }
        }
        return printed;
    }

    private static List<OrderLine> collectDeltaLines(List<OrderLine> lines) {
        List<OrderLine> deltas = new ArrayList<>();
        for (OrderLine line : lines) {
            int delta = line.getCount() - line.getPrintedCount();
            if (delta > 0) {
                deltas.add(copyWithCount(line, delta));
            }
        }
        return deltas;
    }

    private static OrderLine copyWithCount(OrderLine source, int count) {
        OrderLine copy = new OrderLine();
        copy.setId(source.getId());
        copy.setTableId(source.getTableId());
        copy.setMenuItemId(source.getMenuItemId());
        copy.setName(source.getName());
        copy.setItemQuantity(source.getItemQuantity());
        copy.setUnit(source.getUnit());
        copy.setCategory(source.getCategory());
        copy.setPrice(source.getPrice());
        copy.setCount(count);
        copy.setPrintedCount(0);
        return copy;
    }

    static String lineKey(OrderLine line) {
        return line.getMenuItemId() + "|" + line.getName() + "|" + line.getCategory().name();
    }
}
