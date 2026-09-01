package com.example.deviceinfo.server;

import com.example.deviceinfo.model.MenuCategory;
import com.example.deviceinfo.model.MenuItem;
import com.example.deviceinfo.model.MenuSection;
import com.example.deviceinfo.model.OrderLine;
import com.example.deviceinfo.model.RestaurantTable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ApiJsonParser {

    private ApiJsonParser() {
    }

    public static JSONObject menuToJson(MenuItem item) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", item.getId());
        o.put("name", item.getName());
        o.put("description", item.getDescription());
        o.put("quantity", item.getQuantity());
        o.put("unit", item.getUnit());
        o.put("category", item.getCategory().name());
        o.put("categoryText", item.getCategoryText());
        o.put("amountText", item.getAmountText() != null ? item.getAmountText() : "");
        o.put("section", item.getSection().name());
        o.put("complex", item.isComplex());
        o.put("price", item.getPrice());
        return o;
    }

    public static JSONObject tableToJson(RestaurantTable table) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", table.getId());
        o.put("number", table.getNumber());
        o.put("label", table.getDisplayLabel());
        o.put("description", table.getDescription());
        o.put("status", table.getStatus().name());
        o.put("openOrderNumber", table.getOpenOrderNumber());
        return o;
    }

    public static JSONObject orderLineToJson(OrderLine line) throws Exception {
        JSONObject o = new JSONObject();
        o.put("menuItemId", line.getMenuItemId());
        o.put("name", line.getName());
        o.put("itemQuantity", line.getItemQuantity());
        o.put("unit", line.getUnit());
        o.put("category", line.getCategory().name());
        o.put("price", line.getPrice());
        o.put("count", line.getCount());
        o.put("printedCount", line.getPrintedCount());
        return o;
    }

    public static MenuItem menuFromJson(JSONObject o) throws Exception {
        MenuCategory category = MenuCategory.fromString(o.getString("category"));
        MenuSection section = MenuSection.fromString(o.optString("section", MenuSection.MAIN.name()));
        MenuItem item = new MenuItem(
                o.optLong("id", 0),
                o.getString("name"),
                o.optString("description", ""),
                o.optDouble("quantity", 1),
                o.optString("unit", "шт"),
                category,
                section,
                o.optBoolean("complex", false),
                o.optDouble("price", 0)
        );
        item.setCategoryText(o.optString("categoryText", ""));
        item.setAmountText(o.optString("amountText", ""));
        return item;
    }

    public static List<MenuItem> parseMenuItems(JSONArray array) throws Exception {
        List<MenuItem> items = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            items.add(menuFromJson(array.getJSONObject(i)));
        }
        return items;
    }

    public static List<OrderLine> parseOrderLines(JSONArray array) throws Exception {
        List<OrderLine> lines = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            OrderLine line = new OrderLine();
            line.setMenuItemId(o.optLong("menuItemId", 0));
            line.setName(o.getString("name"));
            line.setItemQuantity(o.getDouble("itemQuantity"));
            line.setUnit(o.getString("unit"));
            line.setCategory(MenuCategory.fromString(o.getString("category")));
            line.setPrice(o.getDouble("price"));
            line.setCount(o.getInt("count"));
            line.setPrintedCount(o.optInt("printedCount", 0));
            lines.add(line);
        }
        return lines;
    }
}
