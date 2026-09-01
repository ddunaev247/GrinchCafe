package com.grinchcafe.admin.util;

import android.content.Context;

import com.grinchcafe.admin.db.DatabaseHelper;
import com.grinchcafe.admin.model.MenuItem;
import com.grinchcafe.admin.net.AdminClient;

import org.json.JSONArray;
import org.json.JSONObject;

public final class MenuSyncHelper {

    private MenuSyncHelper() {
    }

    public static void pushToServer(Context context) throws Exception {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        JSONArray menu = new JSONArray();
        for (MenuItem item : db.getAllMenuItems()) {
            menu.put(menuToJson(item));
        }
        JSONObject body = new JSONObject();
        body.put("menu", menu);
        body.put("complexPrice", db.getComplexPrice());
        AdminClient.pushMenu(context, body.toString());
    }

    private static JSONObject menuToJson(MenuItem item) throws Exception {
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
}
