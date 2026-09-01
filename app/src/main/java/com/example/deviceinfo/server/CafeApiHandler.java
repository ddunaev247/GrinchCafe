package com.example.deviceinfo.server;

import android.content.Context;
import android.util.Log;

import com.example.deviceinfo.db.DatabaseHelper;
import com.example.deviceinfo.model.MenuItem;
import com.example.deviceinfo.model.OrderHistory;
import com.example.deviceinfo.model.OrderLine;
import com.example.deviceinfo.model.ReportPeriod;
import com.example.deviceinfo.model.RestaurantTable;
import com.example.deviceinfo.model.TableStatus;
import com.example.deviceinfo.util.ErrorLogHelper;
import com.example.deviceinfo.util.OrderEvents;
import com.example.deviceinfo.util.PrintDispatcher;
import com.example.deviceinfo.util.PrintSessionHelper;
import com.example.deviceinfo.util.ReportBuilder;
import com.example.deviceinfo.util.ReportRange;
import com.example.deviceinfo.util.ReportStats;
import com.example.deviceinfo.util.TableOrderHistoryBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class CafeApiHandler implements SimpleHttpServer.Handler {

    private static final String TAG = "CafeApiHandler";
    private final Context appContext;

    public CafeApiHandler(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public HttpResponse handle(String method, String path, String body) {
        try {
            if ("GET".equals(method) && "/api/sync".equals(path)) {
                return HttpResponse.ok(buildSyncJson());
            }
            if ("GET".equals(method) && "/api/health".equals(path)) {
                return HttpResponse.ok("{\"status\":\"ok\",\"app\":\"GrinchCafe\"}");
            }
            if ("POST".equals(method) && "/api/print".equals(path)) {
                return handlePrint(body);
            }
            if ("POST".equals(method) && "/api/print-full".equals(path)) {
                return handlePrintFull(body);
            }
            if ("POST".equals(method) && "/api/paid".equals(path)) {
                return handlePaid(body);
            }
            if ("POST".equals(method) && "/api/order/update".equals(path)) {
                return handleOrderUpdate(body);
            }
            if ("POST".equals(method) && "/api/menu/sync".equals(path)) {
                return handleMenuSync(body);
            }
            if ("GET".equals(method) && path.startsWith("/api/reports")) {
                return handleReports(path);
            }
            if ("GET".equals(method) && path.startsWith("/api/table-history")) {
                return handleTableHistory(path);
            }
            return HttpResponse.error(404, "{\"error\":\"not_found\"}");
        } catch (Exception e) {
            Log.e(TAG, "API error", e);
            ErrorLogHelper.log(appContext, method + " " + path, e);
            return HttpResponse.error(500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private HttpResponse handlePrint(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        JSONArray linesArray = json.getJSONArray("lines");
        List<OrderLine> lines = ApiJsonParser.parseOrderLines(linesArray);

        DatabaseHelper db = DatabaseHelper.getInstance(appContext);
        RestaurantTable table = resolveTable(db, json);
        if (table == null) {
            ErrorLogHelper.log(appContext, "API печать", "table_not_found, " + tableRef(json));
            return HttpResponse.error(404, "{\"error\":\"table_not_found\"}");
        }
        int tableNumber = table.getNumber();

        syncOrderLinesToTable(db, table.getId(), lines);

        if (!lines.isEmpty() && table.getStatus() == TableStatus.FREE) {
            table.setStatus(TableStatus.BUSY);
            db.updateTable(table);
        }

        PrintSessionHelper.PrintPlan plan =
                PrintSessionHelper.buildPlan(appContext, db, table.getId(), tableNumber);
        if (plan == null || plan.isEmpty()) {
            ErrorLogHelper.log(appContext, "API печать",
                    "nothing_to_print, стол №" + tableNumber);
            return HttpResponse.error(400, "{\"error\":\"nothing_to_print\"}");
        }

        dispatchPrint(plan, true);
        OrderEvents.notifyChanged(appContext);

        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("orderNumber", plan.orderNumber);
        return HttpResponse.ok(response.toString());
    }

    private HttpResponse handlePrintFull(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        JSONArray linesArray = json.getJSONArray("lines");
        List<OrderLine> lines = ApiJsonParser.parseOrderLines(linesArray);

        DatabaseHelper db = DatabaseHelper.getInstance(appContext);
        RestaurantTable table = resolveTable(db, json);
        if (table == null) {
            ErrorLogHelper.log(appContext, "API полный чек", "table_not_found, " + tableRef(json));
            return HttpResponse.error(404, "{\"error\":\"table_not_found\"}");
        }
        int tableNumber = table.getNumber();

        syncOrderLinesToTable(db, table.getId(), lines);

        if (!lines.isEmpty() && table.getStatus() == TableStatus.FREE) {
            table.setStatus(TableStatus.BUSY);
            db.updateTable(table);
        }

        PrintSessionHelper.PrintPlan plan =
                PrintSessionHelper.buildFullBarPlan(appContext, db, table.getId(), tableNumber);
        if (plan == null || plan.isEmpty()) {
            ErrorLogHelper.log(appContext, "API полный чек",
                    "nothing_to_print, стол №" + tableNumber);
            return HttpResponse.error(400, "{\"error\":\"nothing_to_print\"}");
        }

        dispatchPrint(plan, false);
        OrderEvents.notifyChanged(appContext);

        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("orderNumber", plan.orderNumber);
        return HttpResponse.ok(response.toString());
    }

    private HttpResponse handleOrderUpdate(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        List<OrderLine> lines = ApiJsonParser.parseOrderLines(json.getJSONArray("lines"));

        DatabaseHelper db = DatabaseHelper.getInstance(appContext);
        RestaurantTable table = resolveTable(db, json);
        if (table == null) {
            ErrorLogHelper.log(appContext, "API обновление заказа",
                    "table_not_found, " + tableRef(json));
            return HttpResponse.error(404, "{\"error\":\"table_not_found\"}");
        }

        int clientOrderNumber = json.optInt("openOrderNumber", -1);
        List<OrderLine> currentLines = db.getOrderLines(table.getId());
        if (clientOrderNumber > 0
                && table.getOpenOrderNumber() == 0
                && currentLines.isEmpty()) {
            JSONObject ignored = new JSONObject();
            ignored.put("success", true);
            ignored.put("ignored", true);
            return HttpResponse.ok(ignored.toString());
        }

        syncOrderLinesToTable(db, table.getId(), lines);
        if (!lines.isEmpty() && table.getStatus() == TableStatus.FREE) {
            table.setStatus(TableStatus.BUSY);
            db.updateTable(table);
        } else if (lines.isEmpty() && table.getStatus() == TableStatus.BUSY) {
            table.setStatus(TableStatus.FREE);
            db.updateTable(table);
        }

        OrderEvents.notifyChanged(appContext);
        return HttpResponse.ok("{\"success\":true}");
    }

    private HttpResponse handlePaid(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        String itemsText = json.optString("itemsText", "");
        double totalAmount = json.optDouble("totalAmount", 0);
        int itemCount = json.optInt("itemCount", 0);
        int orderNumber = json.optInt("orderNumber", 0);

        DatabaseHelper db = DatabaseHelper.getInstance(appContext);
        RestaurantTable table = resolveTable(db, json);
        if (table == null) {
            ErrorLogHelper.log(appContext, "API оплата", "table_not_found, " + tableRef(json));
            return HttpResponse.error(404, "{\"error\":\"table_not_found\"}");
        }
        int tableNumber = table.getNumber();

        if (orderNumber <= 0) {
            orderNumber = PrintSessionHelper.resolveOrderNumberForPayment(
                    db, table.getId(), table.getOpenOrderNumber());
        }

        OrderHistory history = new OrderHistory();
        history.setPaidAt(System.currentTimeMillis());
        history.setTableNumber(tableNumber);
        history.setItemsText(itemsText);
        history.setTotalAmount(totalAmount);
        history.setItemCount(itemCount);
        history.setOrderNumber(orderNumber);
        db.insertOrderHistory(history);

        db.clearOrderLines(table.getId());
        PrintSessionHelper.clearSession(db, table.getId());
        table.setStatus(TableStatus.FREE);
        db.updateTable(table);

        OrderEvents.notifyChanged(appContext);
        return HttpResponse.ok("{\"success\":true}");
    }

    private RestaurantTable resolveTable(DatabaseHelper db, JSONObject json) throws Exception {
        long tableId = json.optLong("tableId", 0);
        if (tableId > 0) {
            RestaurantTable byId = db.getTable(tableId);
            if (byId != null) {
                return byId;
            }
        }
        if (json.has("tableNumber")) {
            return db.getTableByNumber(json.getInt("tableNumber"));
        }
        return null;
    }

    private static String tableRef(JSONObject json) {
        long tableId = json.optLong("tableId", 0);
        if (tableId > 0) {
            return "id=" + tableId;
        }
        return "стол №" + json.optInt("tableNumber", 0);
    }

    private void syncOrderLinesToTable(DatabaseHelper db, long tableId, List<OrderLine> incoming) {
        List<OrderLine> existing = db.getOrderLines(tableId);
        PrintSessionHelper.preservePrintedCounts(existing, incoming);
        db.clearOrderLines(tableId);
        for (OrderLine line : incoming) {
            line.setTableId(tableId);
            line.setId(0);
            db.insertOrderLine(line);
        }
    }

    private void dispatchPrint(final PrintSessionHelper.PrintPlan plan, final boolean markDelivered) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                DatabaseHelper db = DatabaseHelper.getInstance(appContext);
                PrintDispatcher.Result result =
                        PrintDispatcher.dispatchSync(appContext, plan);
                if (result.anyDelivered()) {
                    if (markDelivered) {
                        PrintSessionHelper.markPrintDelivered(db, plan);
                    }
                } else {
                    ErrorLogHelper.log(appContext, "Печать (API)",
                            "не удалось отправить чек заказа №" + plan.orderNumber);
                }
            }
        }).start();
    }

    private HttpResponse handleMenuSync(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        List<MenuItem> items = ApiJsonParser.parseMenuItems(json.getJSONArray("menu"));
        DatabaseHelper db = DatabaseHelper.getInstance(appContext);
        db.replaceAllMenuItems(items);
        if (json.has("complexPrice")) {
            db.setComplexPrice(json.getDouble("complexPrice"));
        }
        OrderEvents.notifyChanged(appContext);
        return HttpResponse.ok("{\"success\":true}");
    }

    private HttpResponse handleTableHistory(String path) throws Exception {
        int tableNumber = -1;
        int limit = TableOrderHistoryBuilder.MAX_ENTRIES;
        int q = path.indexOf('?');
        if (q >= 0) {
            for (String param : path.substring(q + 1).split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                if ("tableNumber".equals(kv[0])) {
                    tableNumber = Integer.parseInt(kv[1]);
                } else if ("limit".equals(kv[0])) {
                    limit = Integer.parseInt(kv[1]);
                }
            }
        }
        if (tableNumber <= 0) {
            return HttpResponse.error(400, "{\"error\":\"table_number_required\"}");
        }
        if (limit <= 0) {
            limit = TableOrderHistoryBuilder.MAX_ENTRIES;
        }

        DatabaseHelper db = DatabaseHelper.getInstance(appContext);
        List<OrderHistory> history = db.getRecentPaidOrdersForTable(tableNumber, limit);
        JSONArray orders = new JSONArray();
        for (OrderHistory entry : history) {
            JSONObject order = new JSONObject();
            order.put("orderNumber", entry.getOrderNumber());
            order.put("paidAt", entry.getPaidAt());
            order.put("itemsText", entry.getItemsText());
            order.put("totalAmount", entry.getTotalAmount());
            order.put("itemCount", entry.getItemCount());
            orders.put(order);
        }
        JSONObject response = new JSONObject();
        response.put("orders", orders);
        return HttpResponse.ok(response.toString());
    }

    private HttpResponse handleReports(String path) throws Exception {
        ReportPeriod period = ReportPeriod.DAY;
        long anchorMs = System.currentTimeMillis();
        int q = path.indexOf('?');
        if (q >= 0) {
            for (String param : path.substring(q + 1).split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                if ("period".equals(kv[0])) {
                    period = ReportPeriod.valueOf(kv[1]);
                } else if ("anchorMs".equals(kv[0])) {
                    anchorMs = Long.parseLong(kv[1]);
                }
            }
        }

        DatabaseHelper db = DatabaseHelper.getInstance(appContext);
        long firstOrder = db.getFirstOrderPaidAt();
        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(anchorMs);
        ReportRange range = ReportRange.forAnchor(period, anchor, firstOrder);
        List<OrderHistory> history = db.getOrderHistory(range.fromInclusive, range.toExclusive);
        DatabaseHelper.HistoryAggregate aggregate = db.getHistoryAggregate(
                range.fromInclusive, range.toExclusive);

        JSONObject response = new JSONObject();
        response.put("orderCount", aggregate.orderCount);
        response.put("itemCount", aggregate.itemCount);
        response.put("total", ReportStats.formatMoney(ReportStats.money(aggregate.totalAmount)));
        response.put("periodLabel", range.navLabel);
        response.put("periodLine", range.formatPeriodLine());
        response.put("detailsText", ReportBuilder.buildReport(range, history));
        response.put("fileSlug", range.fileSlug());
        response.put("firstOrderAt", firstOrder);

        if (aggregate.orderCount > 0) {
            BigDecimal total = ReportStats.money(aggregate.totalAmount);
            BigDecimal average = total.divide(
                    BigDecimal.valueOf(aggregate.orderCount), 2, RoundingMode.HALF_UP);
            response.put("average", ReportStats.formatMoney(average));
        } else {
            response.put("average", "");
        }

        if (period == ReportPeriod.MONTH) {
            ReportStats stats = ReportStats.fromHistory(history);
            if (stats.byDay.isEmpty()) {
                response.put("monthActivity", "");
            } else {
                StringBuilder sb = new StringBuilder();
                for (ReportStats.DayStats day : stats.sortedDays()) {
                    sb.append(ReportBuilder.formatDayDisplay(day.dayKey))
                            .append(": ")
                            .append(day.orderCount).append(" зак., ")
                            .append(day.itemCount).append(" поз., ")
                            .append(ReportStats.formatMoney(day.totalAmount))
                            .append(" руб.\n");
                }
                response.put("monthActivity", sb.toString().trim());
            }
        } else {
            response.put("monthActivity", "");
        }

        return HttpResponse.ok(response.toString());
    }

    private String buildSyncJson() throws Exception {
        DatabaseHelper db = DatabaseHelper.getInstance(appContext);
        JSONObject root = new JSONObject();

        JSONArray menu = new JSONArray();
        for (MenuItem item : db.getAllMenuItems()) {
            menu.put(ApiJsonParser.menuToJson(item));
        }
        root.put("menu", menu);

        JSONArray tables = new JSONArray();
        JSONArray orders = new JSONArray();
        for (RestaurantTable table : db.getAllTables()) {
            tables.put(ApiJsonParser.tableToJson(table));
            List<OrderLine> lines = db.getOrderLines(table.getId());
            if (!lines.isEmpty()) {
                JSONObject order = new JSONObject();
                order.put("tableId", table.getId());
                order.put("tableNumber", table.getNumber());
                order.put("openOrderNumber", table.getOpenOrderNumber());
                JSONArray linesJson = new JSONArray();
                for (OrderLine line : lines) {
                    linesJson.put(ApiJsonParser.orderLineToJson(line));
                }
                order.put("lines", linesJson);
                orders.put(order);
            }
        }
        root.put("tables", tables);
        root.put("orders", orders);
        root.put("orderCounter", db.getCurrentOrderNumber());
        root.put("complexPrice", db.getComplexPrice());
        root.put("syncTime", System.currentTimeMillis());
        return root.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
