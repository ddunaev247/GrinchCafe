package com.example.deviceinfo.util;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Пишет только важные ошибки в Downloads: AppName_yyyy-MM-dd_log.txt */
public final class ErrorLogHelper {

    private static final SimpleDateFormat FILE_DATE =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat LINE_TIME =
            new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());

    private ErrorLogHelper() {
    }

    public static void log(Context context, String action, Throwable error) {
        String detail = error == null ? "unknown" : error.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = error == null ? "unknown" : error.getClass().getSimpleName();
        }
        log(context, action, detail);
    }

    public static void log(Context context, String action, String error) {
        if (context == null) {
            return;
        }
        final Context app = context.getApplicationContext();
        final String actionText = action == null || action.trim().isEmpty() ? "-" : action.trim();
        final String errorText = error == null || error.trim().isEmpty() ? "unknown" : error.trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                writeLine(app, actionText, errorText);
            }
        }).start();
    }

    private static void writeLine(Context context, String action, String error) {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) {
                return;
            }
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            String appName = sanitize(context.getString(com.example.deviceinfo.R.string.app_name));
            File file = new File(dir, appName + "_" + FILE_DATE.format(new Date()) + "_log.txt");
            String line = LINE_TIME.format(new Date()) + " | " + action + " | " + error + "\n";
            OutputStreamWriter writer = null;
            try {
                writer = new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8");
                writer.write(line);
                writer.flush();
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "App";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
