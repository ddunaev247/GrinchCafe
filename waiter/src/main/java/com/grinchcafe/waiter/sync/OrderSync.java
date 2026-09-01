package com.grinchcafe.waiter.sync;

import android.content.Context;

import com.grinchcafe.waiter.model.OrderLine;
import com.grinchcafe.waiter.net.CafeClient;
import com.grinchcafe.waiter.net.ServerConfig;
import com.grinchcafe.waiter.util.ErrorLogHelper;

import java.util.ArrayList;
import java.util.List;

public final class OrderSync {

    private OrderSync() {
    }

    public static void pushAsync(final Context context, final long tableId, final int tableNumber,
                                 final int openOrderNumber, final List<OrderLine> lines) {
        if (!ServerConfig.isConfigured(context)) {
            return;
        }
        final List<OrderLine> copy = new ArrayList<>(lines);
        new Thread(new Runnable() {
            @Override
            public void run() {
                pushWithRetry(context, tableId, tableNumber, openOrderNumber, copy, 2);
            }
        }).start();
    }

    public static void pushSync(Context context, long tableId, int tableNumber, int openOrderNumber,
                                List<OrderLine> lines) throws Exception {
        if (!ServerConfig.isConfigured(context)) {
            return;
        }
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                CafeClient.updateOrder(context, tableId, tableNumber, openOrderNumber,
                        new ArrayList<>(lines));
                return;
            } catch (Exception e) {
                last = e;
                Thread.sleep(300);
            }
        }
        if (last != null) {
            throw last;
        }
    }

    private static void pushWithRetry(Context context, long tableId, int tableNumber,
                                      int openOrderNumber, List<OrderLine> lines, int attempts) {
        for (int i = 0; i < attempts; i++) {
            try {
                CafeClient.updateOrder(context, tableId, tableNumber, openOrderNumber, lines);
                return;
            } catch (Exception ignored) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
        ErrorLogHelper.log(context, "Синхронизация заказа",
                "не удалось отправить заказ стола id=" + tableId + " №" + tableNumber);
    }
}
