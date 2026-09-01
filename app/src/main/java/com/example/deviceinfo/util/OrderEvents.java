package com.example.deviceinfo.util;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

public final class OrderEvents {

    public static final String ACTION_ORDER_CHANGED = "com.grinchcafe.main.ORDER_CHANGED";

    private OrderEvents() {
    }

    public static void notifyChanged(Context context) {
        LocalBroadcastManager.getInstance(context.getApplicationContext())
                .sendBroadcast(new Intent(ACTION_ORDER_CHANGED));
    }
}
