package com.grinchcafe.admin;

import android.app.Application;

import com.grinchcafe.admin.net.ServerConfig;
import com.grinchcafe.admin.sync.SyncManager;

public class AdminApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (ServerConfig.isConfigured(this)) {
            SyncManager.getInstance(this).start();
        }
    }
}
