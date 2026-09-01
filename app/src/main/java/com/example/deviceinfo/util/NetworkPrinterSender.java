package com.example.deviceinfo.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class NetworkPrinterSender {

    public interface SendCallback {
        void onSuccess();
        void onFailure(String message);
    }

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int SO_TIMEOUT_MS = 10000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NetworkPrinterSender() {
    }

    public static void sendKitchenIfConfigured(final Context context,
                                               final ReceiptFormatter.KitchenReceipt receipt,
                                               final SendCallback callback) {
        if (!NetworkPrinterConfig.isConfigured(context) || receipt == null) {
            return;
        }
        final String host = NetworkPrinterConfig.getHost(context);
        final int port = NetworkPrinterConfig.getPort(context);
        sendKitchenAsync(context, host, port, receipt, callback);
    }

    public static void sendKitchenAsync(final Context context, final String host, final int port,
                                        final ReceiptFormatter.KitchenReceipt receipt,
                                        final SendCallback callback) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sendKitchenSync(app, host, port, receipt);
                    notifySuccess(callback);
                } catch (final Exception e) {
                    String msg = e.getMessage() == null ? "Ошибка сетевой печати" : e.getMessage();
                    notifyFailure(callback, msg);
                }
            }
        }).start();
    }

    public static void sendKitchenSync(Context context, String host, int port,
                                       ReceiptFormatter.KitchenReceipt receipt) throws Exception {
        byte[] data = EscPosEncoder.buildNetworkKitchenReceiptJob(context, receipt);
        sendRaw(host, port, data);
    }

    public static void sendIfConfigured(final Context context, final String receiptText,
                                        final SendCallback callback) {
        sendIfConfigured(context, java.util.Collections.singletonList(receiptText), callback);
    }

    public static void sendIfConfigured(final Context context, final java.util.List<String> receiptTexts,
                                        final SendCallback callback) {
        if (!NetworkPrinterConfig.isConfigured(context)) {
            return;
        }
        final String host = NetworkPrinterConfig.getHost(context);
        final int port = NetworkPrinterConfig.getPort(context);
        sendAsync(context, host, port, receiptTexts, callback);
    }

    public static void sendAsync(final Context context, final String host, final int port,
                                 final String receiptText, final SendCallback callback) {
        sendAsync(context, host, port, java.util.Collections.singletonList(receiptText), callback);
    }

    public static void sendAsync(final Context context, final String host, final int port,
                                 final java.util.List<String> receiptTexts,
                                 final SendCallback callback) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sendSync(app, host, port, receiptTexts);
                    notifySuccess(callback);
                } catch (final Exception e) {
                    String msg = e.getMessage() == null ? "Ошибка сетевой печати" : e.getMessage();
                    notifyFailure(callback, msg);
                }
            }
        }).start();
    }

    public static void sendSync(Context context, String host, int port, String receiptText) throws Exception {
        sendSync(context, host, port, java.util.Collections.singletonList(receiptText));
    }

    public static void sendSync(Context context, String host, int port,
                                java.util.List<String> receiptTexts) throws Exception {
        byte[] data = EscPosEncoder.buildNetworkReceiptJob(context, receiptTexts);
        sendRaw(host, port, data);
    }

    private static void sendRaw(String host, int port, byte[] data) throws Exception {
        Socket socket = null;
        OutputStream out = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(SO_TIMEOUT_MS);
            out = socket.getOutputStream();
            out.write(data);
            out.flush();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void notifySuccess(final SendCallback callback) {
        if (callback == null) {
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess();
            }
        });
    }

    private static void notifyFailure(final SendCallback callback, final String message) {
        if (callback == null) {
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.onFailure(message);
            }
        });
    }
}
