package com.grinchcafe.waiter.net;

import android.content.Context;

import com.grinchcafe.waiter.model.OrderLine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

public final class CafeClient {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int CONNECT_MS = 8000;
    private static final int READ_SYNC_MS = 15000;
    private static final int READ_ACTION_MS = 20000;

    private CafeClient() {
    }

    public static JSONObject sync(Context context) throws Exception {
        return get(context, "/api/sync", READ_SYNC_MS);
    }

    public static JSONObject getTableHistory(Context context, int tableNumber, int limit) throws Exception {
        return get(context, "/api/table-history?tableNumber=" + tableNumber + "&limit=" + limit, READ_SYNC_MS);
    }

    public static JSONObject print(Context context, long tableId, int tableNumber, List<OrderLine> lines)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId);
        body.put("tableNumber", tableNumber);
        body.put("lines", linesToJson(lines));
        return post(context, "/api/print", body.toString(), READ_ACTION_MS);
    }

    public static JSONObject printFull(Context context, long tableId, int tableNumber, List<OrderLine> lines)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId);
        body.put("tableNumber", tableNumber);
        body.put("lines", linesToJson(lines));
        return post(context, "/api/print-full", body.toString(), READ_ACTION_MS);
    }

    public static JSONObject updateOrder(Context context, long tableId, int tableNumber,
                                         int openOrderNumber, List<OrderLine> lines)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId);
        body.put("tableNumber", tableNumber);
        body.put("openOrderNumber", openOrderNumber);
        body.put("lines", linesToJson(lines));
        return post(context, "/api/order/update", body.toString(), READ_ACTION_MS);
    }

    public static JSONObject paid(Context context, long tableId, int tableNumber, int orderNumber,
                                  String itemsText, double totalAmount, int itemCount) throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId);
        body.put("tableNumber", tableNumber);
        body.put("orderNumber", orderNumber);
        body.put("itemsText", itemsText);
        body.put("totalAmount", totalAmount);
        body.put("itemCount", itemCount);
        return post(context, "/api/paid", body.toString(), READ_ACTION_MS);
    }

    private static JSONArray linesToJson(List<OrderLine> lines) throws Exception {
        JSONArray array = new JSONArray();
        for (OrderLine line : lines) {
            JSONObject o = new JSONObject();
            o.put("menuItemId", line.getMenuItemId());
            o.put("name", line.getName());
            o.put("itemQuantity", line.getItemQuantity());
            o.put("unit", line.getUnit());
            o.put("category", line.getCategory().name());
            o.put("price", line.getPrice());
            o.put("count", line.getCount());
            array.put(o);
        }
        return array;
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
