package com.grinchcafe.admin.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ReportFileHelper {

    private ReportFileHelper() {
    }

    public static File saveToDownloads(Context context, String suggestedName, String content)
            throws IOException {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            throw new IOException("Downloads unavailable");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create Downloads");
        }

        String base = sanitize(suggestedName);
        if (base.length() == 0) {
            base = "GrinchCafe_otchet";
        }
        File file = uniqueFile(dir, base + ".txt");

        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write(content);
            writer.flush();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        scan.setData(Uri.fromFile(file));
        context.sendBroadcast(scan);
        return file;
    }

    public static String suggestedName(ReportRange range) {
        if (range == null) {
            return "GrinchCafe_otchet";
        }
        return "GrinchCafe_otchet_" + range.fileSlug();
    }

    private static File uniqueFile(File dir, String name) {
        File file = new File(dir, name);
        if (!file.exists()) {
            return file;
        }
        String stamp = new SimpleDateFormat("HHmmss", Locale.US).format(new Date());
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        return new File(dir, stem + "_" + stamp + ext);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
