package com.example.deviceinfo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.example.deviceinfo.server.PrintServerService;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextView tvServer = (TextView) findViewById(R.id.tv_server_address);
        tvServer.setText(getString(R.string.server_address_label,
                PrintServerService.getServerAddress(this)));

        findViewById(R.id.btn_printers).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, PrintersActivity.class));
            }
        });

        findViewById(R.id.btn_receipt_categories).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, ReceiptCategoriesActivity.class));
            }
        });

        findViewById(R.id.btn_device_info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, DeviceInfoActivity.class));
            }
        });

        findViewById(R.id.btn_connected_devices).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, ConnectedDevicesActivity.class));
            }
        });
    }
}
