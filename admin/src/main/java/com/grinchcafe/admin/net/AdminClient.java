package com.grinchcafe.admin.net;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

public final class AdminClient {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int CONNECT_MS = 8000;
    private static final int READ_SYNC_MS = 15000;
    private static final int READ_ACTION_MS = 30000;

    private AdminClient() {
    }

    public static JSONObject sync(Context context) throws Exception {
        return get(context, "/api/sync", READ_SYNC_MS);
    }

    public static JSONObject pushMenu(Context context, String jsonBody) throws Exception {
        return post(context, "/api/menu/sync", jsonBody, READ_ACTION_MS);
    }

    public static JSONObject fetchReports(Context context, String period, long anchorMs) throws Exception {
        String path = "/api/reports?period=" + period + "&anchorMs=" + anchorMs;
        return get(context, path, READ_ACTION_MS);
    }

    private static JSONObject get(Context context, String path, int readTimeout) throws Exception {
        URL url = new URL(ServerConfig.getBaseUrl(context) + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_MS);
        conn.setReadTimeout(readTimeout);
        conn.setRequestMethod("GET");
        conn.setUseCaches(false);
        return readJson(conn);
    }

    private static JSONObject post(Context context, String path, String jsonBody, int readTimeout) throws Exception {
        URL url = new URL(ServerConfig.getBaseUrl(context) + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_MS);
        conn.setReadTimeout(readTimeout);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Connection", "close");
        byte[] bytes = jsonBody.getBytes(UTF8);
        conn.setFixedLengthStreamingMode(bytes.length);
        OutputStream out = conn.getOutputStream();
        out.write(bytes);
        out.flush();
        out.close();
        return readJson(conn);
    }

    private static JSONObject readJson(HttpURLConnection conn) throws Exception {
        try {
            int code = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(), UTF8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            String text = sb.toString().trim();
            if (code >= 400) {
                String detail = text;
                try {
                    if (!text.isEmpty()) {
                        detail = new JSONObject(text).optString("error", text);
                    }
                } catch (Exception ignored) {
                }
                if (detail == null || detail.isEmpty()) {
                    throw new Exception("HTTP " + code);
                }
                throw new Exception("HTTP " + code + ": " + detail);
            }
            if (text.isEmpty()) {
                return new JSONObject();
            }
            return new JSONObject(text);
        } finally {
            conn.disconnect();
        }
    }
}
