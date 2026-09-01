package com.example.deviceinfo;

import android.app.Application;
import android.content.Intent;

import com.example.deviceinfo.server.PrintServerService;
import com.example.deviceinfo.util.MonthlyReportScheduler;
import com.example.deviceinfo.util.NetworkPrinterConfig;

public class RestaurantApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NetworkPrinterConfig.ensureDefaults(this);
        MonthlyReportScheduler.checkAndSaveMonthlyReport(this);
        try {
            startService(new Intent(this, PrintServerService.class));
        } catch (Exception ignored) {
        }
    }
}
