package com.example.deviceinfo.server;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.deviceinfo.util.NetworkPrinterScanner;

public class PrintServerService extends Service {

    private static final String TAG = "PrintServerService";
    public static final int DEFAULT_PORT = 8765;

    private SimpleHttpServer httpServer;

    @Override
    public void onCreate() {
        super.onCreate();
        httpServer = new SimpleHttpServer();
        httpServer.start(DEFAULT_PORT, new CafeApiHandler(this));
        Log.i(TAG, "Print server started on port " + DEFAULT_PORT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (httpServer != null) {
            httpServer.stop();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static String getServerAddress(android.content.Context context) {
        String ip = NetworkPrinterScanner.getLocalIpAddress(context);
        if (ip == null || ip.isEmpty()) {
            return "—";
        }
        return ip + ":" + DEFAULT_PORT;
    }
}
