package com.example.deviceinfo;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;

public class PrinterTestActivity extends AppCompatActivity {

    private TextView tvTestResult;
    private static final String ACTION_USB_PERMISSION = "com.example.deviceinfo.USB_PERMISSION";
    private static final String RECEIPT_TEXT =
            "--------------------------------\n" +
            "            Столик №*\n\n" +
            "1. Борщ 1шт/500г\n" +
            "2. Салат зеленый 1шт/300г\n" +
            "3. Картофель фри 1шт/100г\n" +
            "4. Пиво светлое  2шт/0,66мл\n\n" +
            "--------------------------------\n";
    private UsbDevice targetDevice;
    private UsbManager usbManager;
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            sendTestPrint(device);
                        }
                    } else {
                        tvTestResult.append("\n\nОШИБКА: Разрешение USB не получено!");
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printer_test);

        tvTestResult = (TextView) findViewById(R.id.tv_test_result);
        Button btnSystemPrint = (Button) findViewById(R.id.btn_system_print);
        Button btnUsbPrint = (Button) findViewById(R.id.btn_usb_print);
        Button btnEscPosPrint = (Button) findViewById(R.id.btn_escpos_print);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Register USB permission receiver
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);

        // Get target device from intent
        String printerName = getIntent().getStringExtra("printer_name");
        int vendorId = getIntent().getIntExtra("vendor_id", -1);
        int productId = getIntent().getIntExtra("product_id", -1);

        if (printerName != null) {
            tvTestResult.setText("Выбран принтер: " + printerName + 
                "\nVendor: 0x" + String.format("%04X", vendorId) + 
                "\nProduct: 0x" + String.format("%04X", productId) + 
                "\n\nВыберите способ печати:");

            // Find the device
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            for (UsbDevice dev : deviceList.values()) {
                if (dev.getVendorId() == vendorId && dev.getProductId() == productId) {
                    targetDevice = dev;
                    break;
                }
            }
        } else {
            tvTestResult.setText("Принтер не выбран.\nПерейдите в раздел 'Печатные устройства' и выберите принтер.");
            btnUsbPrint.setEnabled(false);
            btnEscPosPrint.setEnabled(false);
        }

        btnSystemPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                printViaSystem();
            }
        });

        btnUsbPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (targetDevice != null) {
                    if (usbManager.hasPermission(targetDevice)) {
                        sendTestPrint(targetDevice);
                    } else {
                        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                                PrinterTestActivity.this, 0, 
                                new Intent(ACTION_USB_PERMISSION), 0);
                        usbManager.requestPermission(targetDevice, permissionIntent);
                        tvTestResult.append("\n\nЗапрос разрешения USB отправлен...");
                    }
                }
            }
        });

        btnEscPosPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (targetDevice != null) {
                    if (usbManager.hasPermission(targetDevice)) {
                        sendEscPosPrint(targetDevice);
                    } else {
                        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                                PrinterTestActivity.this, 0, 
                                new Intent(ACTION_USB_PERMISSION), 0);
                        usbManager.requestPermission(targetDevice, permissionIntent);
                        tvTestResult.append("\n\nЗапрос разрешения USB отправлен...");
                    }
                }
            }
        });
    }

    private void printViaSystem() {
        tvTestResult.append("\n\n=== Системная печать ===\n");
        tvTestResult.append("Запуск системного диалога печати...\n");

        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        String jobName = getString(R.string.app_name) + " Test Document";

        PrintDocumentAdapter printAdapter = new TestPrintDocumentAdapter(this);

        try {
            printManager.print(jobName, printAdapter, 
                new PrintAttributes.Builder().build());
            tvTestResult.append("Диалог печати открыт.\n");
        } catch (Exception e) {
            tvTestResult.append("ОШИБКА: " + e.getMessage() + "\n");
        }
    }

    private void sendTestPrint(UsbDevice device) {
        tvTestResult.append("\n\n=== Прямая USB печать ===\n");

        UsbInterface usbInterface = null;
        UsbEndpoint endpointOut = null;

        // Find printer interface and OUT endpoint
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
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
            tvTestResult.append("ОШИБКА: Не найден интерфейс принтера или OUT endpoint\n");
            return;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            tvTestResult.append("ОШИБКА: Не удалось открыть соединение с устройством\n");
            return;
        }

        connection.claimInterface(usbInterface, true);

        byte[] data;
        try {
            data = RECEIPT_TEXT.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            data = RECEIPT_TEXT.getBytes();
        }

        int result = connection.bulkTransfer(endpointOut, data, data.length, 5000);

        if (result >= 0) {
            tvTestResult.append("УСПЕХ: Отправлено " + result + " байт\n");
        } else {
            tvTestResult.append("ОШИБКА: Код ошибки " + result + "\n");
        }

        connection.releaseInterface(usbInterface);
        connection.close();
    }

    private void sendEscPosPrint(UsbDevice device) {
        tvTestResult.append("\n\n=== ESC/POS печать ===\n");

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
            tvTestResult.append("ОШИБКА: Не найден интерфейс\n");
            return;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            tvTestResult.append("ОШИБКА: Не удалось открыть соединение\n");
            return;
        }

        connection.claimInterface(usbInterface, true);

        // ESC/POS commands for thermal printers
        byte[] init = new byte[]{0x1B, 0x40}; // Initialize
        byte[] alignCenter = new byte[]{0x1B, 0x61, 0x01}; // Center alignment
        byte[] boldOn = new byte[]{0x1B, 0x45, 0x01}; // Bold ON
        byte[] boldOff = new byte[]{0x1B, 0x45, 0x00}; // Bold OFF
        byte[] doubleHeight = new byte[]{0x1D, 0x21, 0x01}; // Double height
        byte[] normalSize = new byte[]{0x1D, 0x21, 0x00}; // Normal size
        byte[] cutPaper = new byte[]{0x1D, 0x56, 0x00}; // Cut paper (partial)
        byte[] feedLines = new byte[]{0x1B, 0x64, 0x05}; // Feed 5 lines
        byte[] lineFeed = new byte[]{0x0A};

        String titleText = "Столик №*";
        String receiptLines = "1. Борщ 1шт/500г\n" +
                "2. Салат зеленый 1шт/300г\n" +
                "3. Картофель фри 1шт/100г\n" +
                "4. Пиво светлое  2шт/0,66мл";
        String borderText = "--------------------------------";

        byte[] titleBytes;
        try {
            titleBytes = titleText.getBytes("CP866"); // Cyrillic code page for ESC/POS
        } catch (UnsupportedEncodingException e) {
            titleBytes = titleText.getBytes();
        }

        byte[] receiptBytes;
        try {
            receiptBytes = receiptLines.getBytes("CP866");
        } catch (UnsupportedEncodingException e) {
            receiptBytes = receiptLines.getBytes();
        }

        byte[] borderBytes;
        try {
            borderBytes = borderText.getBytes("CP866");
        } catch (UnsupportedEncodingException e) {
            borderBytes = borderText.getBytes();
        }

        // Build command sequence
        int totalLen = init.length + alignCenter.length + boldOn.length + doubleHeight.length + 
                       borderBytes.length + lineFeed.length +
                       titleBytes.length + lineFeed.length + lineFeed.length +
                       normalSize.length + boldOff.length + receiptBytes.length + lineFeed.length +
                       borderBytes.length + lineFeed.length + feedLines.length + cutPaper.length;

        byte[] command = new byte[totalLen];
        int pos = 0;

        System.arraycopy(init, 0, command, pos, init.length); pos += init.length;
        System.arraycopy(alignCenter, 0, command, pos, alignCenter.length); pos += alignCenter.length;
        System.arraycopy(boldOn, 0, command, pos, boldOn.length); pos += boldOn.length;
        System.arraycopy(doubleHeight, 0, command, pos, doubleHeight.length); pos += doubleHeight.length;
        System.arraycopy(borderBytes, 0, command, pos, borderBytes.length); pos += borderBytes.length;
        System.arraycopy(lineFeed, 0, command, pos, lineFeed.length); pos += lineFeed.length;
        System.arraycopy(titleBytes, 0, command, pos, titleBytes.length); pos += titleBytes.length;
        System.arraycopy(lineFeed, 0, command, pos, lineFeed.length); pos += lineFeed.length;
        System.arraycopy(lineFeed, 0, command, pos, lineFeed.length); pos += lineFeed.length;
        System.arraycopy(normalSize, 0, command, pos, normalSize.length); pos += normalSize.length;
        System.arraycopy(boldOff, 0, command, pos, boldOff.length); pos += boldOff.length;
        System.arraycopy(receiptBytes, 0, command, pos, receiptBytes.length); pos += receiptBytes.length;
        System.arraycopy(lineFeed, 0, command, pos, lineFeed.length); pos += lineFeed.length;
        System.arraycopy(borderBytes, 0, command, pos, borderBytes.length); pos += borderBytes.length;
        System.arraycopy(lineFeed, 0, command, pos, lineFeed.length); pos += lineFeed.length;
        System.arraycopy(feedLines, 0, command, pos, feedLines.length); pos += feedLines.length;
        System.arraycopy(cutPaper, 0, command, pos, cutPaper.length); pos += cutPaper.length;

        int result = connection.bulkTransfer(endpointOut, command, command.length, 5000);

        if (result >= 0) {
            tvTestResult.append("УСПЕХ: ESC/POS команды отправлены (" + result + " байт)\n");
            tvTestResult.append("Если принтер поддерживает ESC/POS, должен напечататься шаблон чека\n");
        } else {
            tvTestResult.append("ОШИБКА: Код " + result + "\n");
        }

        connection.releaseInterface(usbInterface);
        connection.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            // Ignore
        }
    }
}
