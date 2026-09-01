package com.example.deviceinfo.util;

import com.example.deviceinfo.model.LunchComplex;
import com.example.deviceinfo.model.MenuCategory;
import com.example.deviceinfo.model.OrderLine;

import java.util.List;

public final class ReceiptFormatter {

    // Replace with your organization's details before production deployment.
    private static final String ORG_NAME = "Example Cafe LLC";
    private static final String BRAND_NAME = "GRINCHCAFE";
    private static final String ORG_ADDRESS = "City, Street, Building";
    private static final String ORG_UNP = "000000000";
    private static final String ORG_RN_SKKO = "000000000";

    /** Кухонный чек: заголовок + тело (позиции). */
    public static final class KitchenReceipt {
        public final String header;
        public final String body;

        public KitchenReceipt(String header, String body) {
            this.header = header;
            this.body = body == null ? "" : body;
        }
    }

    private ReceiptFormatter() {
    }

    public static String formatReceipt(int tableNumber, int orderNumber, List<OrderLine> sortedLines) {
        return formatBarReceipt(tableNumber, orderNumber, sortedLines, false,
                System.currentTimeMillis(), 1);
    }

    /** Полный чек для бара (USB): реквизиты, все позиции, итого. */
    public static String formatBarReceipt(int tableNumber, int orderNumber,
                                          List<OrderLine> lines, boolean additive,
                                          long eventTimeMs, int receiptVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append(ReceiptLayout.center(ORG_NAME)).append('\n');
        sb.append(ReceiptLayout.center(BRAND_NAME)).append('\n');
        sb.append(ReceiptLayout.center(ORG_ADDRESS)).append('\n');
        sb.append(ReceiptLayout.leftRight("УНП", ORG_UNP)).append('\n');
        sb.append(ReceiptLayout.leftRight("РН СККО", ORG_RN_SKKO)).append('\n');
        sb.append(ReceiptLayout.separator('-')).append('\n');
        sb.append(ReceiptLayout.center("Стол №" + tableNumber + "   Заказ №" + orderNumber)).append('\n');
        sb.append(ReceiptLayout.separator('-')).append('\n');

        if (additive) {
            sb.append(ReceiptLayout.center("+++")).append('\n');
        }

        double total = 0;
        for (OrderLine line : lines) {
            sb.append(formatBarLine(line)).append('\n');
            total += line.getLineTotal();
        }

        sb.append(ReceiptLayout.separator('-')).append('\n');
        sb.append(ReceiptLayout.leftRight("общ:", ReceiptLayout.formatMoney(total))).append('\n');
        sb.append(ReceiptLayout.separator('-')).append('\n');
        sb.append(ReceiptLayout.formatReceiptFooter(eventTimeMs, receiptVersion)).append('\n');
        return sb.toString();
    }

    /** Кухня: только рабочая часть (заголовок оформляется в EscPosEncoder). */
    public static KitchenReceipt formatKitchenReceipt(int tableNumber, int orderNumber,
                                                        List<OrderLine> lines, boolean additive,
                                                        long eventTimeMs, int receiptVersion) {
        if (additive) {
            return formatKitchenStyleReceipt(tableNumber, orderNumber,
                    java.util.Collections.<OrderLine>emptyList(), lines,
                    eventTimeMs, receiptVersion);
        }
        return formatKitchenStyleReceipt(tableNumber, orderNumber, null, lines,
                eventTimeMs, receiptVersion);
    }

    /**
     * Рабочий чек (кухня / бар при «Печать»): старые позиции, затем «+++», затем новые.
     * Если старых нет — только новые без разделителя.
     */
    public static KitchenReceipt formatKitchenStyleReceipt(int tableNumber, int orderNumber,
                                                           List<OrderLine> oldLines,
                                                           List<OrderLine> newLines,
                                                           long eventTimeMs, int receiptVersion) {
        String header = "Стол №" + tableNumber + "   Заказ №" + orderNumber;
        StringBuilder body = new StringBuilder();
        boolean hasOld = oldLines != null && !oldLines.isEmpty();
        boolean hasNew = newLines != null && !newLines.isEmpty();
        int index = 1;

        if (hasOld) {
            for (OrderLine line : oldLines) {
                body.append(formatKitchenLine(line, index++)).append('\n');
            }
            if (hasNew) {
                body.append("+++\n");
            }
        }

        if (hasNew) {
            for (OrderLine line : newLines) {
                body.append(formatKitchenLine(line, index++)).append('\n');
            }
        }

        body.append('\n');
        body.append(ReceiptLayout.formatReceiptFooter(eventTimeMs, receiptVersion)).append('\n');
        return new KitchenReceipt(header, body.toString());
    }

    public static boolean isKitchenLine(OrderLine line) {
        return line != null && !MenuCategory.isBarCategory(line.getCategory());
    }

    private static String formatBarLine(OrderLine line) {
        String total = ReceiptLayout.formatMoney(line.getLineTotal());
        if (LunchComplex.isComboLine(line)) {
            String right = line.getCount() + "шт=" + total;
            return ReceiptLayout.leftRight(line.getName(), right);
        }
        String price = ReceiptLayout.formatMoney(line.getPrice());
        String right = line.getCount() + "*" + price + "=" + total;
        return ReceiptLayout.leftRight(line.getName(), right);
    }

    private static String formatKitchenLine(OrderLine line, int index) {
        if (LunchComplex.isComboLine(line)) {
            return index + ". " + line.getName() + " " + line.getCount() + "шт";
        }
        String qty = line.getCount() + "шт/" + formatItemAmount(line);
        return index + ". " + line.getName() + " " + qty;
    }

    private static String formatItemAmount(OrderLine line) {
        double itemQuantity = line.getItemQuantity();
        String unit = line.getUnit();
        if (itemQuantity == 0 && unit != null && unit.trim().length() > 0) {
            return unit.trim();
        }
        String amount = itemQuantity == (long) itemQuantity
                ? String.valueOf((long) itemQuantity)
                : String.valueOf(itemQuantity);
        return amount + unit;
    }
}
