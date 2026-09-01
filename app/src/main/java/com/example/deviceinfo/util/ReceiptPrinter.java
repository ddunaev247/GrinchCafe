package com.example.deviceinfo.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;

public final class ReceiptPrinter {

    public static final String ACTION_USB_PRINT_PERMISSION = "com.example.deviceinfo.USB_PRINT_PERMISSION";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface PrintCallback {
        void onSuccess();
        void onFailure(String message);
        void onPermissionRequired(UsbDevice device);
    }

    private ReceiptPrinter() {
    }

    public static void printBarReceipt(final Context context, final String barReceiptText,
                                       final PrintCallback callback) {
        printBarReceipts(context, java.util.Collections.singletonList(barReceiptText), callback);
    }

    /** USB-бар в кухонном формате (крупный заголовок) — кнопка «Печать». */
    public static void printKitchenStyleReceipt(final Context context,
                                                final ReceiptFormatter.KitchenReceipt receipt,
                                                final PrintCallback callback) {
        if (receipt == null) {
            notifyFailure(callback, "Пустой чек");
            return;
        }
        final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            notifyFailure(callback, "USB недоступен");
            return;
        }

        final UsbDevice device = findPrinterDevice(usbManager);
        if (device == null) {
            notifyFailure(callback, "POS-принтер не найден. Подключите USB-терминал.");
            return;
        }

        if (!usbManager.hasPermission(device)) {
            notifyPermissionRequired(callback, device);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = sendKitchenEscPos(context, usbManager, device, receipt);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            callback.onSuccess();
                        } else {
                            callback.onFailure("Не удалось отправить данные на принтер");
                        }
                    }
                });
            }
        }).start();
    }

    public static boolean printKitchenStyleReceiptSync(Context context,
                                                       ReceiptFormatter.KitchenReceipt receipt) {
        if (receipt == null) {
            return false;
        }
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            return false;
        }
        UsbDevice device = findPrinterDevice(usbManager);
        if (device == null || !usbManager.hasPermission(device)) {
            return false;
        }
        return sendKitchenEscPos(context, usbManager, device, receipt);
    }

    public static void printBarReceipts(final Context context, final java.util.List<String> receiptTexts,
                                        final PrintCallback callback) {
        final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            notifyFailure(callback, "USB недоступен");
            return;
        }

        final UsbDevice device = findPrinterDevice(usbManager);
        if (device == null) {
            notifyFailure(callback, "POS-принтер не найден. Подключите USB-терминал.");
            return;
        }

        if (!usbManager.hasPermission(device)) {
            notifyPermissionRequired(callback, device);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = sendBarEscPos(context, usbManager, device, receiptTexts);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            callback.onSuccess();
                        } else {
                            callback.onFailure("Не удалось отправить данные на принтер");
                        }
                    }
                });
            }
        }).start();
    }

    /** Синхронная печать полного чека бара. */
    public static boolean printBarReceiptSync(Context context, String barReceiptText) {
        return printBarReceiptsSync(context, java.util.Collections.singletonList(barReceiptText));
    }

    public static boolean printBarReceiptsSync(Context context, java.util.List<String> receiptTexts) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            return false;
        }
        UsbDevice device = findPrinterDevice(usbManager);
        if (device == null || !usbManager.hasPermission(device)) {
            return false;
        }
        return sendBarEscPos(context, usbManager, device, receiptTexts);
    }

    public static void printPosReceipt(final Context context, final String receiptText,
                                       final PrintCallback callback) {
        printPosReceipts(context, java.util.Collections.singletonList(receiptText), callback);
    }

    public static void printPosReceipts(final Context context, final java.util.List<String> receiptTexts,
                                        final PrintCallback callback) {
        final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            notifyFailure(callback, "USB недоступен");
            return;
        }

        final UsbDevice device = findPrinterDevice(usbManager);
        if (device == null) {
            notifyFailure(callback, "POS-принтер не найден. Подключите USB-терминал.");
            return;
        }

        if (!usbManager.hasPermission(device)) {
            notifyPermissionRequired(callback, device);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = sendEscPos(context, usbManager, device, receiptTexts);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            callback.onSuccess();
                        } else {
                            callback.onFailure("Не удалось отправить данные на принтер");
                        }
                    }
                });
            }
        }).start();
    }

    /** Синхронная печать для фоновых задач (API официанта). */
    public static boolean printPosReceiptsSync(Context context, java.util.List<String> receiptTexts) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            return false;
        }
        UsbDevice device = findPrinterDevice(usbManager);
        if (device == null || !usbManager.hasPermission(device)) {
            return false;
        }
        return sendEscPos(context, usbManager, device, receiptTexts);
    }

    public static void requestUsbPermission(Context context, UsbDevice device) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null || device == null) {
            return;
        }
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PRINT_PERMISSION), 0);
        usbManager.requestPermission(device, permissionIntent);
    }

    public static UsbDevice findPrinterDevice(UsbManager usbManager) {
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        UsbDevice fallback = null;
        for (UsbDevice device : devices.values()) {
            if (isPrinterDevice(device)) {
                return device;
            }
            if (fallback == null) {
                fallback = device;
            }
        }
        return fallback;
    }

    private static void notifyFailure(final PrintCallback callback, final String message) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.onFailure(message);
            }
        });
    }

    private static void notifyPermissionRequired(final PrintCallback callback, final UsbDevice device) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.onPermissionRequired(device);
            }
        });
    }

    private static boolean isPrinterDevice(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            int cls = iface.getInterfaceClass();
            if (cls == UsbConstants.USB_CLASS_PRINTER ||
                    cls == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                return true;
            }
        }
        return false;
    }

    private static boolean sendEscPos(Context context, UsbManager usbManager, UsbDevice device,
                                      java.util.List<String> receiptTexts) {
        UsbInterface usbInterface = null;
        UsbEndpoint endpointOut = null;

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER ||
                    iface.getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                usbInterface = iface;
                for (int j = 0; j < iface.getEndpointCount(); j++) {
                    UsbEndpoint ep = iface.getEndpoint(j);
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                        endpointOut = ep;
                        break;
                    }
                }
                break;
            }
        }

        if (usbInterface == null || endpointOut == null) {
            return false;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            return false;
        }

        connection.claimInterface(usbInterface, true);
        byte[] command = EscPosEncoder.buildUsbReceiptJob(context, receiptTexts);
        boolean success = bulkTransferChunked(connection, endpointOut, command);
        connection.releaseInterface(usbInterface);
        connection.close();
        return success;
    }

    private static boolean sendBarEscPos(Context context, UsbManager usbManager, UsbDevice device,
                                         java.util.List<String> receiptTexts) {
        UsbInterface usbInterface = null;
        UsbEndpoint endpointOut = null;

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER ||
                    iface.getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                usbInterface = iface;
                for (int j = 0; j < iface.getEndpointCount(); j++) {
                    UsbEndpoint ep = iface.getEndpoint(j);
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                        endpointOut = ep;
                        break;
                    }
                }
                break;
            }
        }

        if (usbInterface == null || endpointOut == null) {
            return false;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            return false;
        }

        connection.claimInterface(usbInterface, true);
        byte[] command = EscPosEncoder.buildUsbBarReceiptJob(context, receiptTexts);
        boolean success = bulkTransferChunked(connection, endpointOut, command);
        connection.releaseInterface(usbInterface);
        connection.close();
        return success;
    }

    private static boolean sendKitchenEscPos(Context context, UsbManager usbManager, UsbDevice device,
                                             ReceiptFormatter.KitchenReceipt receipt) {
        UsbInterface usbInterface = null;
        UsbEndpoint endpointOut = null;

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER ||
                    iface.getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                usbInterface = iface;
                for (int j = 0; j < iface.getEndpointCount(); j++) {
                    UsbEndpoint ep = iface.getEndpoint(j);
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                        endpointOut = ep;
                        break;
                    }
                }
                break;
            }
        }

        if (usbInterface == null || endpointOut == null) {
            return false;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            return false;
        }

        connection.claimInterface(usbInterface, true);
        byte[] command = EscPosEncoder.buildUsbKitchenReceiptJob(context, receipt);
        boolean success = bulkTransferChunked(connection, endpointOut, command);
        connection.releaseInterface(usbInterface);
        connection.close();
        return success;
    }

    private static boolean bulkTransferChunked(UsbDeviceConnection connection,
                                               UsbEndpoint endpointOut, byte[] data) {
        int offset = 0;
        int chunkSize = endpointOut.getMaxPacketSize();
        if (chunkSize <= 0) {
            chunkSize = 512;
        }
        while (offset < data.length) {
            int len = Math.min(chunkSize, data.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(data, offset, chunk, 0, len);
            int result = connection.bulkTransfer(endpointOut, chunk, len, 10000);
            if (result < 0) {
                return false;
            }
            offset += len;
        }
        return true;
    }
}
