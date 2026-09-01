package com.grinchcafe.waiter.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppStateManager {

    private static final String PREFS = "app_state_prefs";
    private static final String KEY_LAST_TABLE_ID = "last_table_id";

    private AppStateManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void setLastTableId(Context context, long tableId) {
        prefs(context).edit().putLong(KEY_LAST_TABLE_ID, tableId).apply();
    }

    public static long getLastTableId(Context context) {
        return prefs(context).getLong(KEY_LAST_TABLE_ID, -1);
    }

    public static void clearLastTableId(Context context) {
        prefs(context).edit().remove(KEY_LAST_TABLE_ID).apply();
    }
}
