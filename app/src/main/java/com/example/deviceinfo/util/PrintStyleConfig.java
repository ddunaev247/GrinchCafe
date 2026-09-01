package com.example.deviceinfo.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Настройки ESC/POS: высота, ширина символов и жирность текста на чеке.
 */
public final class PrintStyleConfig {

    public static final int SIZE_NORMAL = 0;
    public static final int SIZE_DOUBLE = 1;

    private static final String PREFS = "print_style_prefs";
    private static final String KEY_DOUBLE_HEIGHT = "double_height";
    private static final String KEY_DOUBLE_WIDTH = "double_width";
    private static final String KEY_BOLD = "bold";

    private PrintStyleConfig() {
    }

    public static final class Style {
        public final boolean doubleHeight;
        public final boolean doubleWidth;
        public final boolean bold;

        public Style(boolean doubleHeight, boolean doubleWidth, boolean bold) {
            this.doubleHeight = doubleHeight;
            this.doubleWidth = doubleWidth;
            this.bold = bold;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Style getStyle(Context context) {
        SharedPreferences p = prefs(context);
        return new Style(
                p.getBoolean(KEY_DOUBLE_HEIGHT, false),
                p.getBoolean(KEY_DOUBLE_WIDTH, false),
                p.getBoolean(KEY_BOLD, false));
    }

    public static int getHeightSetting(Context context) {
        return getStyle(context).doubleHeight ? SIZE_DOUBLE : SIZE_NORMAL;
    }

    public static int getWidthSetting(Context context) {
        return getStyle(context).doubleWidth ? SIZE_DOUBLE : SIZE_NORMAL;
    }

    public static boolean isBold(Context context) {
        return prefs(context).getBoolean(KEY_BOLD, false);
    }

    public static void save(Context context, int heightSetting, int widthSetting, boolean bold) {
        prefs(context).edit()
                .putBoolean(KEY_DOUBLE_HEIGHT, heightSetting == SIZE_DOUBLE)
                .putBoolean(KEY_DOUBLE_WIDTH, widthSetting == SIZE_DOUBLE)
                .putBoolean(KEY_BOLD, bold)
                .apply();
    }
}
