package com.example.deviceinfo;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.text.format.Formatter;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

public class DeviceInfoActivity extends AppCompatActivity {

    private TextView tvDeviceInfo;

    @SuppressLint("HardwareIds")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);

        tvDeviceInfo = (TextView) findViewById(R.id.tv_device_info);
        tvDeviceInfo.setText(buildDeviceInfo());
    }

    private String buildDeviceInfo() {
        StringBuilder sb = new StringBuilder();

        // OS Information
        sb.append("=== ИНФОРМАЦИЯ ОБ ОС ===\n\n");
        sb.append("Android Version: ").append(Build.VERSION.RELEASE).append("\n");
        sb.append("SDK Level: ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Codename: ").append(Build.VERSION.CODENAME).append("\n");
        sb.append("Security Patch: ").append(Build.VERSION.SECURITY_PATCH).append("\n");
        sb.append("Base OS: ").append(Build.VERSION.BASE_OS).append("\n");
        sb.append("Incremental: ").append(Build.VERSION.INCREMENTAL).append("\n");

        // Device Information
        sb.append("\n=== ИНФОРМАЦИЯ ОБ УСТРОЙСТВЕ ===\n\n");
        sb.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        sb.append("Brand: ").append(Build.BRAND).append("\n");
        sb.append("Model: ").append(Build.MODEL).append("\n");
        sb.append("Device: ").append(Build.DEVICE).append("\n");
        sb.append("Product: ").append(Build.PRODUCT).append("\n");
        sb.append("Board: ").append(Build.BOARD).append("\n");
        sb.append("Hardware: ").append(Build.HARDWARE).append("\n");
        sb.append("Bootloader: ").append(Build.BOOTLOADER).append("\n");
        sb.append("Radio Version: ").append(Build.getRadioVersion()).append("\n");
        sb.append("Display: ").append(Build.DISPLAY).append("\n");
        sb.append("Fingerprint: ").append(Build.FINGERPRINT).append("\n");
        sb.append("ID: ").append(Build.ID).append("\n");
        sb.append("Tags: ").append(Build.TAGS).append("\n");
        sb.append("Type: ").append(Build.TYPE).append("\n");
        sb.append("User: ").append(Build.USER).append("\n");
        sb.append("Host: ").append(Build.HOST).append("\n");
        sb.append("Time: ").append(Build.TIME).append("\n");

        // CPU Information
        sb.append("\n=== ПРОЦЕССОР ===\n\n");
        sb.append("CPU ABI: ").append(Build.CPU_ABI).append("\n");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sb.append("Supported ABIs: ").append(java.util.Arrays.toString(Build.SUPPORTED_ABIS)).append("\n");
        }
        sb.append("Cores: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        sb.append("Max Memory: ").append(Formatter.formatFileSize(this, Runtime.getRuntime().maxMemory())).append("\n");

        // RAM Info
        ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        sb.append("\n=== ПАМЯТЬ ===\n\n");
        sb.append("Total RAM: ").append(Formatter.formatFileSize(this, memInfo.totalMem)).append("\n");
        sb.append("Available RAM: ").append(Formatter.formatFileSize(this, memInfo.availMem)).append("\n");
        sb.append("Low Memory: ").append(memInfo.lowMemory).append("\n");

        // Storage Info
        sb.append("\n=== ХРАНИЛИЩЕ ===\n\n");
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        sb.append("Internal Total: ").append(Formatter.formatFileSize(this, totalBlocks * blockSize)).append("\n");
        sb.append("Internal Available: ").append(Formatter.formatFileSize(this, availableBlocks * blockSize)).append("\n");

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File extPath = Environment.getExternalStorageDirectory();
            StatFs extStat = new StatFs(extPath.getPath());
            long extBlockSize = extStat.getBlockSizeLong();
            long extTotalBlocks = extStat.getBlockCountLong();
            long extAvailableBlocks = extStat.getAvailableBlocksLong();
            sb.append("External Total: ").append(Formatter.formatFileSize(this, extTotalBlocks * extBlockSize)).append("\n");
            sb.append("External Available: ").append(Formatter.formatFileSize(this, extAvailableBlocks * extBlockSize)).append("\n");
        }

        // Screen Info
        sb.append("\n=== ЭКРАН ===\n\n");
        sb.append("Density: ").append(getResources().getDisplayMetrics().density).append("\n");
        sb.append("Density DPI: ").append(getResources().getDisplayMetrics().densityDpi).append("\n");
        sb.append("Width Pixels: ").append(getResources().getDisplayMetrics().widthPixels).append("\n");
        sb.append("Height Pixels: ").append(getResources().getDisplayMetrics().heightPixels).append("\n");
        sb.append("Scaled Density: ").append(getResources().getDisplayMetrics().scaledDensity).append("\n");
        sb.append("XDPI: ").append(getResources().getDisplayMetrics().xdpi).append("\n");
        sb.append("YDPI: ").append(getResources().getDisplayMetrics().ydpi).append("\n");

        // WiFi Info
        sb.append("\n=== СЕТЬ ===\n\n");
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            sb.append("WiFi SSID: ").append(wifiInfo.getSSID()).append("\n");
            sb.append("WiFi BSSID: ").append(wifiInfo.getBSSID()).append("\n");
            sb.append("WiFi MAC: ").append(wifiInfo.getMacAddress()).append("\n");
            sb.append("WiFi IP: ").append(Formatter.formatIpAddress(wifiInfo.getIpAddress())).append("\n");
            sb.append("WiFi Link Speed: ").append(wifiInfo.getLinkSpeed()).append(" Mbps\n");
            sb.append("WiFi Frequency: ").append(wifiInfo.getFrequency()).append(" MHz\n");
            sb.append("WiFi RSSI: ").append(wifiInfo.getRssi()).append(" dBm\n");
            sb.append("Network ID: ").append(wifiInfo.getNetworkId()).append("\n");
        } else {
            sb.append("WiFi: выключен или недоступен\n");
        }

        // Telephony
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            sb.append("\n=== ТЕЛЕФОНИЯ ===\n\n");
            sb.append("Device ID: ").append(telephonyManager.getDeviceId()).append("\n");
            sb.append("Subscriber ID: ").append(telephonyManager.getSubscriberId()).append("\n");
            sb.append("Sim Operator: ").append(telephonyManager.getSimOperatorName()).append("\n");
            sb.append("Network Operator: ").append(telephonyManager.getNetworkOperatorName()).append("\n");
            sb.append("Phone Type: ").append(telephonyManager.getPhoneType()).append("\n");
            sb.append("Network Type: ").append(telephonyManager.getNetworkType()).append("\n");
            sb.append("Sim State: ").append(telephonyManager.getSimState()).append("\n");
            sb.append("Sim Country: ").append(telephonyManager.getSimCountryIso()).append("\n");
            sb.append("Network Country: ").append(telephonyManager.getNetworkCountryIso()).append("\n");
        }

        // Battery (requires receiver, simplified here)
        sb.append("\n=== БАТАРЕЯ ===\n\n");
        sb.append("(Для детальной информации о батарее используйте системные настройки)\n");

        return sb.toString();
    }
}
