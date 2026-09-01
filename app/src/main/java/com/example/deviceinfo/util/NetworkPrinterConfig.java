package com.example.deviceinfo.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class NetworkPrinterConfig {

    private static final String PREFS = "network_printer_prefs";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_INITIALIZED = "initialized";

    public static final String DEFAULT_HOST = "192.168.0.150";
    public static final int DEFAULT_PORT = 9100;

    private NetworkPrinterConfig() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void ensureDefaults(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(KEY_INITIALIZED, false)) {
            p.edit()
                    .putString(KEY_HOST, DEFAULT_HOST)
                    .putInt(KEY_PORT, DEFAULT_PORT)
                    .putBoolean(KEY_ENABLED, true)
                    .putBoolean(KEY_INITIALIZED, true)
                    .apply();
        }
    }

    public static boolean isConfigured(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean(KEY_ENABLED, false) && !getHost(context).isEmpty();
    }

    public static String getHost(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.contains(KEY_HOST)) {
            return DEFAULT_HOST;
        }
        return p.getString(KEY_HOST, DEFAULT_HOST);
    }

    public static int getPort(Context context) {
        return prefs(context).getInt(KEY_PORT, DEFAULT_PORT);
    }

    public static void save(Context context, String host, int port, boolean enabled) {
        prefs(context).edit()
                .putString(KEY_HOST, host == null ? "" : host.trim())
                .putInt(KEY_PORT, port > 0 ? port : DEFAULT_PORT)
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_INITIALIZED, true)
                .apply();
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }
}
