package com.example.deviceinfo;

import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.deviceinfo.util.NetworkPrinterConfig;
import com.example.deviceinfo.util.NetworkPrinterDevice;
import com.example.deviceinfo.util.NetworkPrinterScanner;
import com.example.deviceinfo.util.NetworkPrinterSender;
import com.example.deviceinfo.util.PrintStyleConfig;
import com.example.deviceinfo.util.ReceiptFormatter;
import com.example.deviceinfo.model.OrderLine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class PrintersActivity extends AppCompatActivity {

    private LinearLayout layoutUsbPrinters;
    private LinearLayout layoutNetworkFound;
    private TextView tvPrinterSummary;
    private TextView tvLocalIp;
    private TextView tvNetworkStatus;
    private TextView tvScanProgress;
    private EditText etNetworkHost;
    private EditText etNetworkPort;
    private CheckBox cbNetworkEnabled;
    private Spinner spinnerPrintHeight;
    private Spinner spinnerPrintWidth;
    private CheckBox cbPrintBold;
    private ProgressBar progressScan;
    private boolean scanning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printers);

        layoutUsbPrinters = (LinearLayout) findViewById(R.id.layout_usb_printers);
        layoutNetworkFound = (LinearLayout) findViewById(R.id.layout_network_found);
        tvPrinterSummary = (TextView) findViewById(R.id.tv_printer_summary);
        tvLocalIp = (TextView) findViewById(R.id.tv_local_ip);
        tvNetworkStatus = (TextView) findViewById(R.id.tv_network_printer_status);
        tvScanProgress = (TextView) findViewById(R.id.tv_scan_progress);
        etNetworkHost = (EditText) findViewById(R.id.et_network_host);
        etNetworkPort = (EditText) findViewById(R.id.et_network_port);
        cbNetworkEnabled = (CheckBox) findViewById(R.id.cb_network_enabled);
        spinnerPrintHeight = (Spinner) findViewById(R.id.spinner_print_height);
        spinnerPrintWidth = (Spinner) findViewById(R.id.spinner_print_width);
        cbPrintBold = (CheckBox) findViewById(R.id.cb_print_bold);
        progressScan = (ProgressBar) findViewById(R.id.progress_scan);

        ArrayAdapter<CharSequence> sizeAdapter = ArrayAdapter.createFromResource(
                this, R.array.print_size_options, android.R.layout.simple_spinner_item);
        sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPrintHeight.setAdapter(sizeAdapter);
        spinnerPrintWidth.setAdapter(sizeAdapter);

        findViewById(R.id.btn_refresh_usb).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                discoverUsbPrinters();
            }
        });

        findViewById(R.id.btn_scan_network).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanNetwork();
            }
        });

        findViewById(R.id.btn_save_network).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveNetworkPrinter();
            }
        });

        findViewById(R.id.btn_test_network).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testNetworkPrint();
            }
        });

        findViewById(R.id.btn_save_print_style).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePrintStyle();
            }
        });

        NetworkPrinterConfig.ensureDefaults(this);
        loadPrintStyleSettings();
        loadNetworkSettings();
        discoverUsbPrinters();
    }

    private void loadPrintStyleSettings() {
        spinnerPrintHeight.setSelection(PrintStyleConfig.getHeightSetting(this));
        spinnerPrintWidth.setSelection(PrintStyleConfig.getWidthSetting(this));
        cbPrintBold.setChecked(PrintStyleConfig.isBold(this));
    }

    private void savePrintStyle() {
        PrintStyleConfig.save(
                this,
                spinnerPrintHeight.getSelectedItemPosition(),
                spinnerPrintWidth.getSelectedItemPosition(),
                cbPrintBold.isChecked());
        Toast.makeText(this, R.string.print_style_saved, Toast.LENGTH_SHORT).show();
    }

    private void loadNetworkSettings() {
        String localIp = NetworkPrinterScanner.getLocalIpAddress(this);
        tvLocalIp.setText(getString(R.string.local_ip_label,
                localIp == null ? "—" : localIp));

        etNetworkHost.setText(NetworkPrinterConfig.getHost(this));
        etNetworkPort.setText(String.valueOf(NetworkPrinterConfig.getPort(this)));
        cbNetworkEnabled.setChecked(NetworkPrinterConfig.isConfigured(this));
        updateNetworkStatus();
    }

    private void updateNetworkStatus() {
        if (NetworkPrinterConfig.isConfigured(this)) {
            tvNetworkStatus.setText(getString(R.string.network_printer_active,
                    NetworkPrinterConfig.getHost(this),
                    NetworkPrinterConfig.getPort(this)));
        } else {
            tvNetworkStatus.setText(R.string.network_printer_inactive);
        }
    }

    private void discoverUsbPrinters() {
        layoutUsbPrinters.removeAllViews();

        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        int printerCount = 0;

        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if (device.getDeviceClass() == 7 || isPrinterDevice(device)) {
                printerCount++;
                addUsbPrinterView(device, usbManager);
            }
        }

        tvPrinterSummary.setText(getString(R.string.usb_printer_count, printerCount));

        if (printerCount == 0) {
            TextView tv = new TextView(this);
            tv.setText(R.string.no_usb_printers);
            tv.setPadding(0, 8, 0, 8);
            tv.setTextColor(getResources().getColor(R.color.textSecondary));
            layoutUsbPrinters.addView(tv);
        }
    }

    private boolean isPrinterDevice(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            if (device.getInterface(i).getInterfaceClass() == 7) {
                return true;
            }
        }
        return false;
    }

    private void addUsbPrinterView(final UsbDevice device, final UsbManager usbManager) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 16, 16, 16);
        card.setBackgroundResource(R.drawable.bg_tile);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 12);
        card.setLayoutParams(params);

        TextView tv = new TextView(this);
        tv.setText(getString(R.string.usb_printer_info,
                device.getDeviceName(),
                String.format("0x%04X", device.getVendorId()),
                String.format("0x%04X", device.getProductId()),
                usbManager.hasPermission(device) ? "да" : "нет"));
        card.addView(tv);

        Button btnTest = new Button(this);
        btnTest.setText(R.string.test_usb_print);
        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PrintersActivity.this, PrinterTestActivity.class);
                intent.putExtra("printer_name", device.getDeviceName());
                intent.putExtra("vendor_id", device.getVendorId());
                intent.putExtra("product_id", device.getProductId());
                startActivity(intent);
            }
        });
        card.addView(btnTest);

        layoutUsbPrinters.addView(card);
    }

    private void scanNetwork() {
        if (scanning) {
            return;
        }
        scanning = true;
        layoutNetworkFound.removeAllViews();
        progressScan.setVisibility(View.VISIBLE);
        progressScan.setProgress(0);
        tvScanProgress.setVisibility(View.VISIBLE);
        tvScanProgress.setText(R.string.scanning_network);

        NetworkPrinterScanner.scanAsync(this, new NetworkPrinterScanner.ScanCallback() {
            @Override
            public void onProgress(final int scanned, final int total) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressScan.setMax(total);
                        progressScan.setProgress(scanned);
                        tvScanProgress.setText(getString(R.string.scan_progress, scanned, total));
                    }
                });
            }

            @Override
            public void onComplete(final List<NetworkPrinterDevice> devices) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        progressScan.setVisibility(View.GONE);
                        tvScanProgress.setVisibility(View.GONE);
                        showFoundDevices(devices);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        progressScan.setVisibility(View.GONE);
                        tvScanProgress.setVisibility(View.GONE);
                        Toast.makeText(PrintersActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showFoundDevices(List<NetworkPrinterDevice> devices) {
        layoutNetworkFound.removeAllViews();

        if (devices.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(R.string.network_scan_empty);
            tv.setTextColor(getResources().getColor(R.color.textSecondary));
            layoutNetworkFound.addView(tv);
            return;
        }

        TextView header = new TextView(this);
        header.setText(getString(R.string.network_scan_found, devices.size()));
        header.setPadding(0, 0, 0, 8);
        layoutNetworkFound.addView(header);

        for (final NetworkPrinterDevice device : devices) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tv.setText(device.getDisplayAddress() + "\n" + device.getLabel());
            row.addView(tv);

            Button btnSelect = new Button(this);
            btnSelect.setText(R.string.select_printer);
            btnSelect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    etNetworkHost.setText(device.getHost());
                    etNetworkPort.setText(String.valueOf(device.getPort()));
                    cbNetworkEnabled.setChecked(true);
                    saveNetworkPrinter();
                }
            });
            row.addView(btnSelect);

            layoutNetworkFound.addView(row);
        }
    }

    private void saveNetworkPrinter() {
        String host = etNetworkHost.getText().toString().trim();
        int port = NetworkPrinterConfig.DEFAULT_PORT;
        try {
            port = Integer.parseInt(etNetworkPort.getText().toString().trim());
        } catch (Exception ignored) {
        }

        if (cbNetworkEnabled.isChecked() && host.isEmpty()) {
            Toast.makeText(this, R.string.network_ip_required, Toast.LENGTH_SHORT).show();
            return;
        }

        NetworkPrinterConfig.save(this, host, port, cbNetworkEnabled.isChecked());
        updateNetworkStatus();
        Toast.makeText(this, R.string.network_printer_saved, Toast.LENGTH_SHORT).show();
    }

    private void testNetworkPrint() {
        final String host = etNetworkHost.getText().toString().trim();
        if (host.isEmpty()) {
            Toast.makeText(this, R.string.network_ip_required, Toast.LENGTH_SHORT).show();
            return;
        }

        int port = NetworkPrinterConfig.DEFAULT_PORT;
        try {
            port = Integer.parseInt(etNetworkPort.getText().toString().trim());
        } catch (Exception ignored) {
        }

        OrderLine sample = new OrderLine();
        sample.setName("Тест");
        sample.setCount(1);
        sample.setItemQuantity(1);
        sample.setUnit("шт");

        java.util.ArrayList<OrderLine> lines = new java.util.ArrayList<>();
        lines.add(sample);
        final String receipt = ReceiptFormatter.formatBarReceipt(
                99, 1, lines, false, System.currentTimeMillis(), 1);
        final int finalPort = port;

        Toast.makeText(this, R.string.network_print_sending, Toast.LENGTH_SHORT).show();
        NetworkPrinterSender.sendAsync(this, host, finalPort, receipt, new NetworkPrinterSender.SendCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PrintersActivity.this,
                                R.string.network_print_success, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PrintersActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}
