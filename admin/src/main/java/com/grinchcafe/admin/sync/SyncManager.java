package com.grinchcafe.admin.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.grinchcafe.admin.db.DatabaseHelper;
import com.grinchcafe.admin.net.AdminClient;
import com.grinchcafe.admin.net.ServerConfig;

import org.json.JSONObject;

import java.util.concurrent.CopyOnWriteArrayList;

public final class SyncManager implements Runnable {

    public interface Listener {
        void onSyncSuccess();
        void onSyncFailed(String message);
    }

    private static final long INTERVAL_MS = 5000;
    private static SyncManager instance;

    private final Context appContext;
    private final Handler mainHandler;
    private final Handler syncHandler;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean running;
    private volatile boolean syncInProgress;
    private volatile boolean paused;
    private volatile boolean pendingManual;

    private SyncManager(Context context) {
        appContext = context.getApplicationContext();
        mainHandler = new Handler(Looper.getMainLooper());
        syncHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized SyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SyncManager(context);
        }
        return instance;
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        syncHandler.post(this);
    }

    public void stop() {
        running = false;
        syncHandler.removeCallbacks(this);
    }

    public void syncNow() {
        if (!ServerConfig.isConfigured(appContext) || syncInProgress || paused) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                performSync(false);
            }
        }).start();
    }

    @Override
    public void run() {
        if (!running) {
            return;
        }
        syncNow();
        syncHandler.postDelayed(this, INTERVAL_MS);
    }

    private void performSync(final boolean fromManual) {
        if (syncInProgress) {
            if (fromManual) {
                pendingManual = true;
            }
            return;
        }
        if (!fromManual && paused) {
            return;
        }
        syncInProgress = true;
        try {
            JSONObject data = AdminClient.sync(appContext);
            DatabaseHelper db = DatabaseHelper.getInstance(appContext);
            db.applySyncData(
                    data.getJSONArray("menu"),
                    data.getJSONArray("tables"),
                    data.optJSONArray("orders"));
            if (data.has("complexPrice")) {
                db.setComplexPrice(data.getDouble("complexPrice"));
            }
            notifySuccess(fromManual);
        } catch (final Exception e) {
            notifyFailure(fromManual, e.getMessage());
        } finally {
            syncInProgress = false;
            if (pendingManual) {
                pendingManual = false;
                performSync(true);
            }
        }
    }

    public void syncManual() {
        if (!ServerConfig.isConfigured(appContext)) {
            notifyFailure(true, "Сервер не настроен");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                performSync(true);
            }
        }).start();
    }

    private void notifySuccess(final boolean fromManual) {
        if (listeners.isEmpty()) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (Listener listener : listeners) {
                    listener.onSyncSuccess();
                }
            }
        });
    }

    private void notifyFailure(final boolean fromManual, final String message) {
        if (listeners.isEmpty() || !fromManual) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                String text = message == null ? "Ошибка" : message;
                for (Listener listener : listeners) {
                    listener.onSyncFailed(text);
                }
            }
        });
    }
}
