package com.grinchcafe.waiter;

import android.app.Application;

import com.grinchcafe.waiter.net.ServerConfig;
import com.grinchcafe.waiter.sync.SyncManager;

public class WaiterApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (ServerConfig.isConfigured(this)) {
            SyncManager.getInstance(this).start();
        }
    }
}
