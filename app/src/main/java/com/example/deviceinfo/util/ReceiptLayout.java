package com.example.deviceinfo.util;

/** Фиксированная ширина строки чека (80 мм, Font A). */
public final class ReceiptLayout {

    public static final int WIDTH = 42;

    private ReceiptLayout() {
    }

    public static String separator(char ch) {
        StringBuilder sb = new StringBuilder(WIDTH);
        for (int i = 0; i < WIDTH; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }

    public static String center(String text) {
        if (text == null) {
            text = "";
        }
        if (text.length() >= WIDTH) {
            return text.substring(0, WIDTH);
        }
        int pad = (WIDTH - text.length()) / 2;
        StringBuilder sb = new StringBuilder(WIDTH);
        for (int i = 0; i < pad; i++) {
            sb.append(' ');
        }
        sb.append(text);
        while (sb.length() < WIDTH) {
            sb.append(' ');
        }
        return sb.toString();
    }

    public static String leftRight(String left, String right) {
        if (left == null) {
            left = "";
        }
        if (right == null) {
            right = "";
        }
        if (right.length() >= WIDTH) {
            return right.substring(0, WIDTH);
        }
        int maxLeft = WIDTH - right.length();
        if (left.length() > maxLeft) {
            left = left.substring(0, Math.max(0, maxLeft - 1)).trim() + ".";
        }
        StringBuilder sb = new StringBuilder(WIDTH);
        sb.append(left);
        while (sb.length() < WIDTH - right.length()) {
            sb.append(' ');
        }
        sb.append(right);
        return sb.toString();
    }

    public static String formatMoney(double amount) {
        return String.format(java.util.Locale.US, "%.2f", amount);
    }

    /** Дата/время слева, номер версии чека справа. */
    public static String formatReceiptFooter(long eventTimeMs, int receiptVersion) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                "dd.MM.yyyy HH:mm", java.util.Locale.getDefault());
        String dateTime = sdf.format(new java.util.Date(eventTimeMs));
        return leftRight(dateTime, "v." + receiptVersion);
    }
}
