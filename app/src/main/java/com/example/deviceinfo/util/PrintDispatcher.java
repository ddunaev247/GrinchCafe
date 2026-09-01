package com.example.deviceinfo.util;

import android.content.Context;
import android.util.Log;

/**
 * Единая отправка чека на оба принтера терминала.
 * «Печать»: USB и сеть — рабочий (кухонный) формат.
 * «Полный чек»: только USB — гостевой чек.
 */
public final class PrintDispatcher {

    private static final String TAG = "PrintDispatcher";

    public static final class Result {
        public final boolean usbDelivered;
        public final boolean networkDelivered;

        public Result(boolean usbDelivered, boolean networkDelivered) {
            this.usbDelivered = usbDelivered;
            this.networkDelivered = networkDelivered;
        }

        public boolean anyDelivered() {
            return usbDelivered || networkDelivered;
        }
    }

    private PrintDispatcher() {
    }

    /** Синхронная печать (API официанта, фоновые задачи). */
    public static Result dispatchSync(Context context, PrintSessionHelper.PrintPlan plan) {
        Context app = context.getApplicationContext();

        boolean usbDelivered = false;
        if (plan != null && plan.hasBarKitchenPrint()) {
            usbDelivered = ReceiptPrinter.printKitchenStyleReceiptSync(app, plan.barKitchenReceipt);
            if (!usbDelivered) {
                ErrorLogHelper.log(app, "USB печать (бар)", "не удалось отправить на USB-принтер");
            }
        } else if (plan != null && plan.hasGuestBarPrint()) {
            usbDelivered = ReceiptPrinter.printBarReceiptSync(app, plan.barReceipt);
            if (!usbDelivered) {
                ErrorLogHelper.log(app, "USB печать (бар)", "не удалось отправить на USB-принтер");
            }
        }

        boolean networkDelivered = false;
        if (plan != null && plan.hasKitchenPrint() && NetworkPrinterConfig.isConfigured(app)) {
            try {
                NetworkPrinterSender.sendKitchenSync(
                        app,
                        NetworkPrinterConfig.getHost(app),
                        NetworkPrinterConfig.getPort(app),
                        plan.kitchenReceipt);
                networkDelivered = true;
                Log.i(TAG, "Network print ok (kitchen)");
            } catch (Exception e) {
                Log.w(TAG, "Network print failed: " + e.getMessage());
                ErrorLogHelper.log(app, "Сетевая печать (кухня)", e);
            }
        }

        return new Result(usbDelivered, networkDelivered);
    }
}
