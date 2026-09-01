package com.example.deviceinfo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.PendingIntent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class ConnectedDevicesActivity extends AppCompatActivity {

    private LinearLayout layoutUsbDevices;
    private LinearLayout layoutBluetoothDevices;
    private TextView tvUsbSummary;
    private TextView tvBtSummary;
    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connected_devices);

        layoutUsbDevices = (LinearLayout) findViewById(R.id.layout_usb_devices);
        layoutBluetoothDevices = (LinearLayout) findViewById(R.id.layout_bluetooth_devices);
        tvUsbSummary = (TextView) findViewById(R.id.tv_usb_summary);
        tvBtSummary = (TextView) findViewById(R.id.tv_bt_summary);
        Button btnRefresh = (Button) findViewById(R.id.btn_refresh);
        Button btnScanBt = (Button) findViewById(R.id.btn_scan_bt);

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshDevices();
            }
        });

        btnScanBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanBluetooth();
            }
        });

        refreshDevices();
    }

    private void refreshDevices() {
        loadUsbDevices();
        loadBluetoothDevices();
    }

    private void loadUsbDevices() {
        layoutUsbDevices.removeAllViews();
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

        tvUsbSummary.setText("USB устройств найдено: " + deviceList.size());

        if (deviceList.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("USB устройства не обнаружены.\nПодключите устройство через OTG или USB-порт.");
            tv.setPadding(16, 16, 16, 16);
            layoutUsbDevices.addView(tv);
            return;
        }

        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            addUsbDeviceView(device, usbManager);
        }
    }

    private void addUsbDeviceView(final UsbDevice device, final UsbManager usbManager) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        card.setBackgroundColor(0xFFEEEEEE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);

        StringBuilder sb = new StringBuilder();
        sb.append("Имя: ").append(device.getDeviceName()).append("\n");
        sb.append("Vendor ID: ").append(String.format("0x%04X", device.getVendorId())).append("\n");
        sb.append("Product ID: ").append(String.format("0x%04X", device.getProductId())).append("\n");
        sb.append("Class: ").append(device.getDeviceClass()).append(" (").append(getUsbClassName(device.getDeviceClass())).append(")\n");
        sb.append("Subclass: ").append(device.getDeviceSubclass()).append("\n");
        sb.append("Protocol: ").append(device.getDeviceProtocol()).append("\n");
        sb.append("Interface Count: ").append(device.getInterfaceCount()).append("\n");
        sb.append("Has Permission: ").append(usbManager.hasPermission(device)).append("\n");

        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextIsSelectable(true);
        card.addView(tv);

        // Interaction buttons
        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);

        Button btnRequest = new Button(this);
        btnRequest.setText("Запросить доступ");
        btnRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!usbManager.hasPermission(device)) {
                    PendingIntent permissionIntent = PendingIntent.getBroadcast(
                            ConnectedDevicesActivity.this, 0, 
                            new Intent("com.example.deviceinfo.USB_PERMISSION"), 0);
                    usbManager.requestPermission(device, permissionIntent);
                    Toast.makeText(ConnectedDevicesActivity.this, 
                        "Запрос разрешения отправлен", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ConnectedDevicesActivity.this, 
                        "Доступ уже предоставлен", Toast.LENGTH_SHORT).show();
                }
            }
        });
        btnLayout.addView(btnRequest);

        card.addView(btnLayout);
        layoutUsbDevices.addView(card);
    }

    private String getUsbClassName(int cls) {
        switch (cls) {
            case 0: return "Per Interface";
            case 1: return "Audio";
            case 2: return "Communications";
            case 3: return "Human Interface";
            case 5: return "Physical";
            case 6: return "Image";
            case 7: return "Printer";
            case 8: return "Mass Storage";
            case 9: return "Hub";
            case 10: return "CDC Data";
            case 11: return "Smart Card";
            case 13: return "Content Security";
            case 14: return "Video";
            case 15: return "Personal Healthcare";
            case 16: return "Audio/Video";
            case 17: return "Diagnostic";
            case 18: return "Wireless Controller";
            case 19: return "Miscellaneous";
            case 20: return "Application Specific";
            case 21: return "Vendor Specific";
            default: return "Unknown (" + cls + ")";
        }
    }

    private void loadBluetoothDevices() {
        layoutBluetoothDevices.removeAllViews();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            tvBtSummary.setText("Bluetooth не поддерживается на этом устройстве");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            tvBtSummary.setText("Bluetooth выключен");
            TextView tv = new TextView(this);
            tv.setText("Включите Bluetooth для просмотра устройств.");
            tv.setPadding(16, 16, 16, 16);
            layoutBluetoothDevices.addView(tv);
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        tvBtSummary.setText("Сопряженных устройств: " + pairedDevices.size());

        if (pairedDevices.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Нет сопряженных Bluetooth устройств.\nНажмите 'Сканировать' для поиска.");
            tv.setPadding(16, 16, 16, 16);
            layoutBluetoothDevices.addView(tv);
            return;
        }

        for (BluetoothDevice device : pairedDevices) {
            addBluetoothDeviceView(device, true);
        }
    }

    private void addBluetoothDeviceView(BluetoothDevice device, boolean isPaired) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        card.setBackgroundColor(0xFFEEEEEE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);

        StringBuilder sb = new StringBuilder();
        sb.append("Имя: ").append(device.getName() != null ? device.getName() : "Unknown").append("\n");
        sb.append("Адрес: ").append(device.getAddress()).append("\n");
        sb.append("Тип: ").append(getBluetoothType(device.getType())).append("\n");
        sb.append("Статус сопряжения: ").append(isPaired ? "Сопряжено" : "Не сопряжено").append("\n");
        sb.append("Class: ").append(device.getBluetoothClass() != null ? 
            device.getBluetoothClass().getDeviceClass() : "N/A").append("\n");
        sb.append("Bond State: ").append(device.getBondState()).append("\n");

        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextIsSelectable(true);
        card.addView(tv);

        layoutBluetoothDevices.addView(card);
    }

    private String getBluetoothType(int type) {
        switch (type) {
            case BluetoothDevice.DEVICE_TYPE_CLASSIC: return "Classic";
            case BluetoothDevice.DEVICE_TYPE_DUAL: return "Dual Mode";
            case BluetoothDevice.DEVICE_TYPE_LE: return "Low Energy";
            default: return "Unknown";
        }
    }

    private void scanBluetooth() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth недоступен или выключен", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Сканирование запущено...", Toast.LENGTH_SHORT).show();
        bluetoothAdapter.startDiscovery();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(btReceiver, filter);
    }

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    addBluetoothDeviceView(device, false);
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(btReceiver);
        } catch (Exception e) {
            // Receiver not registered
        }
    }
}
