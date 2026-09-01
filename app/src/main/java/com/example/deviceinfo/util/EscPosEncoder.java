package com.example.deviceinfo.util;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * ESC/POS encoder for PR300 / ATOM-216AM and compatible terminals.
 * Uses CP866 (DOS Cyrillic) — standard for Russian ESC/POS printers.
 */
public final class EscPosEncoder {

    private static final Charset CYRILLIC = Charset.forName("IBM866");

    private EscPosEncoder() {
    }

    /** USB — полный чек бара (шаблон с реквизитами). */
    public static byte[] buildUsbBarReceiptJob(Context context, String receiptText) {
        return buildUsbBarReceiptJob(context, java.util.Collections.singletonList(receiptText));
    }

    public static byte[] buildUsbBarReceiptJob(Context context, List<String> receiptTexts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (receiptTexts != null) {
                for (String receiptText : receiptTexts) {
                    if (receiptText != null && receiptText.length() > 0) {
                        writeBarReceipt(out, receiptText);
                    }
                }
            }
            writeSerialPassThrough(out);
        } catch (IOException ignored) {
        }
        return out.toByteArray();
    }

    /** Сеть — кухонный чек: крупный жирный заголовок, обычные позиции. */
    public static byte[] buildNetworkKitchenReceiptJob(Context context,
                                                       ReceiptFormatter.KitchenReceipt receipt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (receipt != null) {
                writeKitchenReceipt(out, receipt);
            }
        } catch (IOException ignored) {
        }
        return out.toByteArray();
    }

    /** USB — тот же кухонный формат (для кнопки «Печать» на баре). */
    public static byte[] buildUsbKitchenReceiptJob(Context context,
                                                   ReceiptFormatter.KitchenReceipt receipt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (receipt != null) {
                writeKitchenReceipt(out, receipt);
            }
            writeSerialPassThrough(out);
        } catch (IOException ignored) {
        }
        return out.toByteArray();
    }

    /** Тестовая / настраиваемая печать (экран принтеров). */
    public static byte[] buildUsbReceiptJob(Context context, String receiptText) {
        return buildUsbReceiptJob(context, java.util.Collections.singletonList(receiptText));
    }

    public static byte[] buildUsbReceiptJob(Context context, List<String> receiptTexts) {
        PrintStyleConfig.Style style = PrintStyleConfig.getStyle(context);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (receiptTexts != null) {
                for (String receiptText : receiptTexts) {
                    if (receiptText != null && receiptText.length() > 0) {
                        writeStyledReceipt(out, receiptText, style);
                    }
                }
            }
            writeSerialPassThrough(out);
        } catch (IOException ignored) {
        }
        return out.toByteArray();
    }

    public static byte[] buildNetworkReceiptJob(Context context, String receiptText) {
        return buildNetworkReceiptJob(context, java.util.Collections.singletonList(receiptText));
    }

    public static byte[] buildNetworkReceiptJob(Context context, List<String> receiptTexts) {
        PrintStyleConfig.Style style = PrintStyleConfig.getStyle(context);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (receiptTexts != null) {
                for (String receiptText : receiptTexts) {
                    if (receiptText != null && receiptText.length() > 0) {
                        writeStyledReceipt(out, receiptText, style);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return out.toByteArray();
    }

    private static void writeBarReceipt(ByteArrayOutputStream out, String receiptText) throws IOException {
        out.write(initSequence());
        out.write(leftAlign());
        out.write(encodeLines(receiptText));
        out.write(feedLines(4));
        out.write(partialCut());
    }

    private static void writeKitchenReceipt(ByteArrayOutputStream out,
                                            ReceiptFormatter.KitchenReceipt receipt) throws IOException {
        out.write(initSequence());
        out.write(centerAlign());
        out.write(bold(true));
        out.write(characterSize(true, true));
        out.write(encodeLines(receipt.header + "\n\n"));
        out.write(characterSize(false, false));
        out.write(bold(false));
        out.write(leftAlign());
        if (receipt.body.length() > 0) {
            out.write(encodeLines(receipt.body));
        }
        out.write(feedLines(4));
        out.write(partialCut());
    }

    private static void writeStyledReceipt(ByteArrayOutputStream out, String receiptText,
                                           PrintStyleConfig.Style style) throws IOException {
        out.write(initSequence());
        out.write(leftAlign());
        if (style.bold) {
            out.write(bold(true));
        }
        out.write(characterSize(style.doubleWidth, style.doubleHeight));
        out.write(encodeLines(receiptText));
        out.write(characterSize(false, false));
        if (style.bold) {
            out.write(bold(false));
        }
        out.write(feedLines(4));
        out.write(partialCut());
    }

    private static void writeSerialPassThrough(ByteArrayOutputStream out) throws IOException {
        out.write(new byte[]{0x1B, 0x3D, 0x02});
        out.write(new byte[]{0x10, 0x04, 0x02});
        out.write(feedLines(2));
        out.write(new byte[]{0x1B, 0x3D, 0x01});
    }

    private static byte[] initSequence() {
        return new byte[]{
                0x1B, 0x40,
                0x1B, 0x74, 0x11
        };
    }

    private static byte[] leftAlign() {
        return new byte[]{0x1B, 0x61, 0x00};
    }

    private static byte[] centerAlign() {
        return new byte[]{0x1B, 0x61, 0x01};
    }

    private static byte[] bold(boolean on) {
        return new byte[]{0x1B, 0x45, (byte) (on ? 0x01 : 0x00)};
    }

    /** GS ! — ширина (бит 4) и высота (бит 0) символа. */
    private static byte[] characterSize(boolean doubleWidth, boolean doubleHeight) {
        int n = 0;
        if (doubleHeight) {
            n |= 0x01;
        }
        if (doubleWidth) {
            n |= 0x10;
        }
        return new byte[]{0x1D, 0x21, (byte) n};
    }

    private static byte[] feedLines(int count) {
        return new byte[]{0x1B, 0x64, (byte) count};
    }

    private static byte[] partialCut() {
        return new byte[]{0x1D, 0x56, 0x00};
    }

    private static byte[] encodeLines(String text) throws UnsupportedEncodingException {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        return normalized.getBytes(CYRILLIC.name());
    }
}
