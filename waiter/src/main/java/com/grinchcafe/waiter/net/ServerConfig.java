package com.grinchcafe.waiter.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

public final class ServerConfig {

    private static final String PREFS = "waiter_server_prefs";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_CONFIGURED = "configured";

    public static final String DEFAULT_HOST = "192.168.0.100";
    public static final int DEFAULT_PORT = 8765;

    private ServerConfig() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isConfigured(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean(KEY_CONFIGURED, false) && !TextUtils.isEmpty(getHost(context));
    }

    public static String getHost(Context context) {
        return prefs(context).getString(KEY_HOST, DEFAULT_HOST);
    }

    public static int getPort(Context context) {
        return prefs(context).getInt(KEY_PORT, DEFAULT_PORT);
    }

    public static void save(Context context, String host, int port) {
        String hostValue = host == null ? DEFAULT_HOST : host.trim();
        int portValue = port > 0 ? port : DEFAULT_PORT;
        int colon = hostValue.lastIndexOf(':');
        if (colon > 0 && colon < hostValue.length() - 1) {
            try {
                portValue = Integer.parseInt(hostValue.substring(colon + 1));
                hostValue = hostValue.substring(0, colon).trim();
            } catch (NumberFormatException ignored) {
            }
        }
        if (hostValue.isEmpty()) {
            hostValue = DEFAULT_HOST;
        }
        prefs(context).edit()
                .putString(KEY_HOST, hostValue)
                .putInt(KEY_PORT, portValue)
                .apply();
    }

    public static void markConfigured(Context context, String host, int port) {
        save(context, host, port);
        prefs(context).edit().putBoolean(KEY_CONFIGURED, true).apply();
    }

    public static String getDisplayAddress(Context context) {
        return getHost(context) + ":" + getPort(context);
    }

    public static String getBaseUrl(Context context) {
        return "http://" + getDisplayAddress(context);
    }
}
