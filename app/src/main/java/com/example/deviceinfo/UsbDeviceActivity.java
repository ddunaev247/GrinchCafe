package com.example.deviceinfo;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.Toast;

public class UsbDeviceActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (device != null) {
            Toast.makeText(this, "USB подключен: " + device.getDeviceName(), Toast.LENGTH_LONG).show();
        }

        finish();
    }
}
