package com.grinchcafe.waiter.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.grinchcafe.waiter.model.MenuCategory;
import com.grinchcafe.waiter.model.MenuItem;
import com.grinchcafe.waiter.model.MenuSection;
import com.grinchcafe.waiter.model.OrderHistory;
import com.grinchcafe.waiter.model.OrderLine;
import com.grinchcafe.waiter.model.RestaurantTable;
import com.grinchcafe.waiter.model.TableStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "restaurant_orders.db";
    private static final int DB_VERSION = 10;
    private static final String META_COMPLEX_PRICE = "complex_price";
    private static final double DEFAULT_COMPLEX_PRICE = 450;

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        try {
            db.enableWriteAheadLogging();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        try {
            db.execSQL("PRAGMA busy_timeout=5000");
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tables (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "number INTEGER NOT NULL," +
                "label TEXT NOT NULL DEFAULT 'Стол'," +
                "description TEXT," +
                "status TEXT NOT NULL," +
                "open_order_number INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE menu_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "description TEXT," +
                "quantity REAL NOT NULL," +
                "unit TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "category_text TEXT," +
                "amount_text TEXT," +
                "section TEXT NOT NULL DEFAULT 'MAIN'," +
                "is_complex INTEGER NOT NULL DEFAULT 0," +
                "price REAL NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE order_lines (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "table_id INTEGER NOT NULL," +
                "menu_item_id INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "item_quantity REAL NOT NULL," +
                "unit TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "price REAL NOT NULL," +
                "count INTEGER NOT NULL DEFAULT 1," +
                "printed_count INTEGER NOT NULL DEFAULT 0," +
                "FOREIGN KEY(table_id) REFERENCES tables(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE order_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "paid_at INTEGER NOT NULL," +
                "table_number INTEGER NOT NULL," +
                "order_number INTEGER NOT NULL DEFAULT 0," +
                "items_text TEXT NOT NULL," +
                "total_amount REAL NOT NULL," +
                "item_count INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE app_meta (" +
                "key TEXT PRIMARY KEY," +
                "value TEXT NOT NULL)");
        db.execSQL("INSERT INTO app_meta (key, value) VALUES ('order_counter', '0')");
        db.execSQL("INSERT INTO app_meta (key, value) VALUES ('complex_price', '450')");
        seedDefaultMenu(db);
        seedDefaultTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            seedDefaultTablesIfEmpty(db);
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS app_meta (" +
                    "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            db.execSQL("INSERT OR IGNORE INTO app_meta (key, value) VALUES ('order_counter', '0')");
            try {
                db.execSQL("ALTER TABLE order_history ADD COLUMN order_number INTEGER NOT NULL DEFAULT 0");
            } catch (Exception ignored) {
            }
        }
        if (oldVersion < 4) {
            migrateMenuSections(db);
        }
        if (oldVersion < 5) {
            applySampleMenuV5(db);
        }
        if (oldVersion < 6) {
            applyLunchComplexV6(db);
        }
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE menu_items ADD COLUMN category_text TEXT");
            } catch (Exception ignored) {
            }
        }
        if (oldVersion < 8) {
            try {
                db.execSQL("ALTER TABLE menu_items ADD COLUMN amount_text TEXT");
            } catch (Exception ignored) {
            }
        }
        if (oldVersion < 9) {
            try {
                db.execSQL("ALTER TABLE order_lines ADD COLUMN printed_count INTEGER NOT NULL DEFAULT 0");
            } catch (Exception ignored) {
            }
            try {
                db.execSQL("ALTER TABLE tables ADD COLUMN open_order_number INTEGER NOT NULL DEFAULT 0");
            } catch (Exception ignored) {
            }
        }
        if (oldVersion < 10) {
            try {
                db.execSQL("ALTER TABLE tables ADD COLUMN label TEXT NOT NULL DEFAULT 'Стол'");
            } catch (Exception ignored) {
            }
        }
    }

    private void migrateMenuSections(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE menu_items ADD COLUMN section TEXT NOT NULL DEFAULT 'MAIN'");
        } catch (Exception ignored) {
        }
        try {
            db.execSQL("ALTER TABLE menu_items ADD COLUMN is_complex INTEGER NOT NULL DEFAULT 0");
        } catch (Exception ignored) {
        }
        Cursor cursor = db.rawQuery("SELECT id, category FROM menu_items", null);
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                MenuCategory category = MenuCategory.fromString(cursor.getString(1));
                ContentValues values = new ContentValues();
                values.put("section", MenuSection.fromCategory(category).name());
                values.put("is_complex", 0);
                db.update("menu_items", values, "id=?", new String[]{String.valueOf(id)});
            }
        } finally {
            cursor.close();
        }
    }

    private void seedDefaultTables(SQLiteDatabase db) {
        for (int i = 1; i <= 8; i++) {
            ContentValues values = new ContentValues();
            values.put("number", i);
            values.put("label", RestaurantTable.DEFAULT_LABEL);
            values.put("description", "Зал, стол " + i);
            values.put("status", TableStatus.FREE.name());
            db.insert("tables", null, values);
        }
    }

    private void seedDefaultTablesIfEmpty(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM tables", null);
        try {
            if (cursor.moveToFirst() && cursor.getInt(0) == 0) {
                seedDefaultTables(db);
            }
        } finally {
            cursor.close();
        }
    }

    public void ensureDefaultTables() {
        seedDefaultTablesIfEmpty(getWritableDatabase());
    }

    private void seedDefaultMenu(SQLiteDatabase db) {
        seedSampleMenu(db);
    }

    /** Демо-позиции для Основного и Обеденного. Бар намеренно пустой. */
    private void seedSampleMenu(SQLiteDatabase db) {
        insertMenuSeed(db, "Стейк из говядины", "С гарниром", 300, "г",
                MenuCategory.MAIN, MenuSection.MAIN, false, 890);
        insertMenuSeed(db, "Паста карбонара", "С беконом", 350, "г",
                MenuCategory.SECOND, MenuSection.MAIN, false, 520);
        insertMenuSeed(db, "Цезарь с курицей", "Классический", 250, "г",
                MenuCategory.SALAD, MenuSection.MAIN, false, 420);
        insertMenuSeed(db, "Картофель фри", "Хрустящий", 150, "г",
                MenuCategory.APPETIZER, MenuSection.MAIN, false, 180);
        insertMenuSeed(db, "Соус барбекю", "К стейку", 50, "г",
                MenuCategory.OTHER, MenuSection.MAIN, false, 80);

        insertMenuSeed(db, "Борщ", "Со сметаной", 400, "г",
                MenuCategory.SOUP, MenuSection.LUNCH, false, 280);
        insertMenuSeed(db, "Харчо", "Острый", 350, "г",
                MenuCategory.SOUP, MenuSection.LUNCH, false, 290);
        insertMenuSeed(db, "Котлета по-киевски", "С пюре", 300, "г",
                MenuCategory.SECOND, MenuSection.LUNCH, false, 350);
        insertMenuSeed(db, "Гречка", "Отварная", 200, "г",
                MenuCategory.SECOND, MenuSection.LUNCH, false, 180);
        insertMenuSeed(db, "Салат витаминный", "Сезонный", 200, "г",
                MenuCategory.SALAD, MenuSection.LUNCH, false, 220);
    }

    private void applySampleMenuV5(SQLiteDatabase db) {
        db.delete("menu_items", "section=?", new String[]{MenuSection.BAR.name()});
        seedSampleMenuMissing(db);
    }

    private void seedSampleMenuMissing(SQLiteDatabase db) {
        insertMenuSeedIfMissing(db, "Стейк из говядины", "С гарниром", 300, "г",
                MenuCategory.MAIN, MenuSection.MAIN, false, 890);
        insertMenuSeedIfMissing(db, "Паста карбонара", "С беконом", 350, "г",
                MenuCategory.SECOND, MenuSection.MAIN, false, 520);
        insertMenuSeedIfMissing(db, "Цезарь с курицей", "Классический", 250, "г",
                MenuCategory.SALAD, MenuSection.MAIN, false, 420);
        insertMenuSeedIfMissing(db, "Картофель фри", "Хрустящий", 150, "г",
                MenuCategory.APPETIZER, MenuSection.MAIN, false, 180);
        insertMenuSeedIfMissing(db, "Соус барбекю", "К стейку", 50, "г",
                MenuCategory.OTHER, MenuSection.MAIN, false, 80);

        insertMenuSeedIfMissing(db, "Борщ", "Со сметаной", 400, "г",
                MenuCategory.SOUP, MenuSection.LUNCH, false, 280);
        insertMenuSeedIfMissing(db, "Харчо", "Острый", 350, "г",
                MenuCategory.SOUP, MenuSection.LUNCH, false, 290);
        insertMenuSeedIfMissing(db, "Котлета по-киевски", "С пюре", 300, "г",
                MenuCategory.SECOND, MenuSection.LUNCH, false, 350);
        insertMenuSeedIfMissing(db, "Гречка", "Отварная", 200, "г",
                MenuCategory.SECOND, MenuSection.LUNCH, false, 180);
        insertMenuSeedIfMissing(db, "Салат витаминный", "Сезонный", 200, "г",
                MenuCategory.SALAD, MenuSection.LUNCH, false, 220);
    }

    private void applyLunchComplexV6(SQLiteDatabase db) {
        db.execSQL("INSERT OR IGNORE INTO app_meta (key, value) VALUES ('complex_price', '450')");
        db.delete("menu_items", "is_complex=?", new String[]{"1"});
        ContentValues soup = new ContentValues();
        soup.put("category", MenuCategory.SOUP.name());
        db.update("menu_items", soup, "section=? AND category=?",
                new String[]{MenuSection.LUNCH.name(), MenuCategory.MAIN.name()});
        seedSampleMenuMissing(db);
    }

    private void insertMenuSeedIfMissing(SQLiteDatabase db, String name, String desc, double qty,
                                         String unit, MenuCategory category, MenuSection section,
                                         boolean complex, double price) {
        Cursor cursor = db.rawQuery("SELECT id FROM menu_items WHERE name=? LIMIT 1",
                new String[]{name});
        try {
            if (cursor.moveToFirst()) {
                return;
            }
        } finally {
            cursor.close();
        }
        insertMenuSeed(db, name, desc, qty, unit, category, section, complex, price);
    }

    private void insertMenuSeed(SQLiteDatabase db, String name, String desc, double qty,
                                String unit, MenuCategory category, MenuSection section,
                                boolean complex, double price) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("description", desc);
        values.put("quantity", qty);
        values.put("unit", unit);
        values.put("category", category.name());
        values.put("section", section.name());
        values.put("is_complex", complex ? 1 : 0);
        values.put("price", price);
        db.insert("menu_items", null, values);
    }

    public void ensureSampleMenu() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("menu_items", "is_complex=?", new String[]{"1"});
        ContentValues soup = new ContentValues();
        soup.put("category", MenuCategory.SOUP.name());
        db.update("menu_items", soup, "section=? AND category=?",
                new String[]{MenuSection.LUNCH.name(), MenuCategory.MAIN.name()});
        ensureComplexPrice(db);
    }

    private void ensureComplexPrice(SQLiteDatabase db) {
        db.execSQL("INSERT OR IGNORE INTO app_meta (key, value) VALUES ('complex_price', '450')");
    }

    public double getComplexPrice() {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT value FROM app_meta WHERE key=?", new String[]{META_COMPLEX_PRICE});
        try {
            if (cursor.moveToFirst()) {
                try {
                    return Double.parseDouble(cursor.getString(0));
                } catch (Exception ignored) {
                    return DEFAULT_COMPLEX_PRICE;
                }
            }
        } finally {
            cursor.close();
        }
        return DEFAULT_COMPLEX_PRICE;
    }

    public void setComplexPrice(double price) {
        SQLiteDatabase db = getWritableDatabase();
        ensureComplexPrice(db);
        ContentValues values = new ContentValues();
        values.put("value", String.valueOf(price));
        int updated = db.update("app_meta", values, "key=?", new String[]{META_COMPLEX_PRICE});
        if (updated == 0) {
            values.put("key", META_COMPLEX_PRICE);
            db.insert("app_meta", null, values);
        }
    }

    // --- Tables ---

    public long insertTable(RestaurantTable table) {
        ContentValues values = new ContentValues();
        values.put("number", table.getNumber());
        values.put("label", table.getDisplayLabel());
        values.put("description", table.getDescription());
        values.put("status", table.getStatus().name());
        return getWritableDatabase().insert("tables", null, values);
    }

    public void updateTable(RestaurantTable table) {
        ContentValues values = new ContentValues();
        values.put("number", table.getNumber());
        values.put("label", table.getDisplayLabel());
        values.put("description", table.getDescription());
        values.put("status", table.getStatus().name());
        getWritableDatabase().update("tables", values, "id=?", new String[]{String.valueOf(table.getId())});
    }

    public void deleteTable(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("order_lines", "table_id=?", new String[]{String.valueOf(id)});
        db.delete("tables", "id=?", new String[]{String.valueOf(id)});
    }

    public RestaurantTable getTable(long id) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, number, description, status, open_order_number, label FROM tables WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            if (cursor.moveToFirst()) {
                return mapTable(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public RestaurantTable getTableByNumber(int number) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, number, description, status, open_order_number, label FROM tables WHERE number=?",
                new String[]{String.valueOf(number)});
        try {
            if (cursor.moveToFirst()) {
                return mapTable(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public synchronized int getNextOrderNumber() {
        SQLiteDatabase db = getWritableDatabase();
        int next = getCurrentOrderNumber() + 1;
        ContentValues values = new ContentValues();
        values.put("value", String.valueOf(next));
        int updated = db.update("app_meta", values, "key=?", new String[]{"order_counter"});
        if (updated == 0) {
            values.put("key", "order_counter");
            db.insert("app_meta", null, values);
        }
        return next;
    }

    public int getCurrentOrderNumber() {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT value FROM app_meta WHERE key='order_counter'", null);
        try {
            if (cursor.moveToFirst()) {
                return Integer.parseInt(cursor.getString(0));
            }
        } catch (Exception ignored) {
        } finally {
            cursor.close();
        }
        return 0;
    }

    public List<RestaurantTable> getAllTables() {
        List<RestaurantTable> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, number, description, status, open_order_number, label FROM tables ORDER BY id ASC", null);
        try {
            while (cursor.moveToNext()) {
                list.add(mapTable(cursor));
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    private RestaurantTable mapTable(Cursor cursor) {
        RestaurantTable table = new RestaurantTable(
                cursor.getLong(0),
                cursor.getInt(1),
                cursor.getString(2),
                TableStatus.fromString(cursor.getString(3)),
                cursor.getInt(4)
        );
        table.setLabel(cursor.getString(5));
        return table;
    }

    // --- Menu ---

    public long insertMenuItem(MenuItem item) {
        ContentValues values = menuValues(item);
        return getWritableDatabase().insert("menu_items", null, values);
    }

    public void updateMenuItem(MenuItem item) {
        getWritableDatabase().update("menu_items", menuValues(item), "id=?",
                new String[]{String.valueOf(item.getId())});
    }

    public void deleteMenuItem(long id) {
        getWritableDatabase().delete("menu_items", "id=?", new String[]{String.valueOf(id)});
    }

    public List<MenuItem> getAllMenuItems() {
        List<MenuItem> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, name, description, quantity, unit, category, category_text, amount_text, price, section, is_complex " +
                        "FROM menu_items ORDER BY name ASC",
                null);
        try {
            while (cursor.moveToNext()) {
                list.add(mapMenuItem(cursor));
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    /**
     * @param complexOnly null — все позиции секции; true — только комплекс; false — без комплекса
     */
    public List<MenuItem> getMenuItemsBySection(MenuSection section, Boolean complexOnly) {
        List<MenuItem> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, name, description, quantity, unit, category, category_text, amount_text, price, section, is_complex " +
                        "FROM menu_items WHERE section=?");
        List<String> args = new ArrayList<>();
        args.add(section.name());
        if (complexOnly != null) {
            sql.append(" AND is_complex=?");
            args.add(complexOnly ? "1" : "0");
        }
        sql.append(" ORDER BY id ASC");
        Cursor cursor = getReadableDatabase().rawQuery(sql.toString(),
                args.toArray(new String[args.size()]));
        try {
            while (cursor.moveToNext()) {
                list.add(mapMenuItem(cursor));
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public MenuItem getMenuItem(long id) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, name, description, quantity, unit, category, category_text, amount_text, price, section, is_complex " +
                        "FROM menu_items WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            if (cursor.moveToFirst()) {
                return mapMenuItem(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private ContentValues menuValues(MenuItem item) {
        ContentValues values = new ContentValues();
        values.put("name", item.getName());
        values.put("description", item.getDescription());
        values.put("quantity", item.getQuantity());
        values.put("unit", item.getUnit());
        values.put("category", item.getCategory().name());
        values.put("category_text", item.getCategoryText());
        values.put("amount_text", item.getAmountText());
        values.put("section", item.getSection().name());
        values.put("is_complex", item.isComplex() ? 1 : 0);
        values.put("price", item.getPrice());
        return values;
    }

    private MenuItem mapMenuItem(Cursor cursor) {
        MenuCategory category = MenuCategory.fromString(cursor.getString(5));
        String categoryText = cursor.getString(6);
        String amountText = cursor.getString(7);
        MenuSection section = MenuSection.fromString(cursor.getString(9));
        boolean complex = cursor.getInt(10) == 1;
        double price = cursor.getDouble(8);

        MenuItem item = new MenuItem(
                cursor.getLong(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getDouble(3),
                cursor.getString(4),
                category,
                section,
                complex,
                price
        );
        item.setCategoryText(categoryText);
        item.setAmountText(amountText);
        return item;
    }

    // --- Order lines ---

    public long insertOrderLine(OrderLine line) {
        ContentValues values = orderLineValues(line);
        return getWritableDatabase().insert("order_lines", null, values);
    }

    public void updateOrderLine(OrderLine line) {
        getWritableDatabase().update("order_lines", orderLineValues(line), "id=?",
                new String[]{String.valueOf(line.getId())});
    }

    public void deleteOrderLine(long id) {
        getWritableDatabase().delete("order_lines", "id=?", new String[]{String.valueOf(id)});
    }

    public void clearOrderLines(long tableId) {
        getWritableDatabase().delete("order_lines", "table_id=?",
                new String[]{String.valueOf(tableId)});
    }

    public List<OrderLine> getOrderLines(long tableId) {
        List<OrderLine> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, table_id, menu_item_id, name, item_quantity, unit, category, price, count, printed_count " +
                        "FROM order_lines WHERE table_id=? ORDER BY id ASC",
                new String[]{String.valueOf(tableId)});
        try {
            while (cursor.moveToNext()) {
                list.add(mapOrderLine(cursor));
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    private ContentValues orderLineValues(OrderLine line) {
        ContentValues values = new ContentValues();
        values.put("table_id", line.getTableId());
        values.put("menu_item_id", line.getMenuItemId());
        values.put("name", line.getName());
        values.put("item_quantity", line.getItemQuantity());
        values.put("unit", line.getUnit());
        values.put("category", line.getCategory().name());
        values.put("price", line.getPrice());
        values.put("count", line.getCount());
        values.put("printed_count", line.getPrintedCount());
        return values;
    }

    private OrderLine mapOrderLine(Cursor cursor) {
        OrderLine line = new OrderLine();
        line.setId(cursor.getLong(0));
        line.setTableId(cursor.getLong(1));
        line.setMenuItemId(cursor.getLong(2));
        line.setName(cursor.getString(3));
        line.setItemQuantity(cursor.getDouble(4));
        line.setUnit(cursor.getString(5));
        line.setCategory(MenuCategory.fromString(cursor.getString(6)));
        line.setPrice(cursor.getDouble(7));
        line.setCount(cursor.getInt(8));
        line.setPrintedCount(cursor.getInt(9));
        return line;
    }

    // --- History ---

    public long insertOrderHistory(OrderHistory history) {
        ContentValues values = new ContentValues();
        values.put("paid_at", history.getPaidAt());
        values.put("table_number", history.getTableNumber());
        values.put("order_number", history.getOrderNumber());
        values.put("items_text", history.getItemsText());
        values.put("total_amount", history.getTotalAmount());
        values.put("item_count", history.getItemCount());
        return getWritableDatabase().insert("order_history", null, values);
    }

    public List<OrderHistory> getOrderHistory(long fromTime, long toTime) {
        List<OrderHistory> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, paid_at, table_number, order_number, items_text, total_amount, item_count " +
                        "FROM order_history WHERE paid_at >= ? AND paid_at < ? ORDER BY paid_at DESC",
                new String[]{String.valueOf(fromTime), String.valueOf(toTime)});
        try {
            while (cursor.moveToNext()) {
                list.add(mapHistory(cursor));
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    private OrderHistory mapHistory(Cursor cursor) {
        return new OrderHistory(
                cursor.getLong(0),
                cursor.getLong(1),
                cursor.getInt(2),
                cursor.getString(4),
                cursor.getDouble(5),
                cursor.getInt(6),
                cursor.getInt(3)
        );
    }

    public void applySyncData(org.json.JSONArray menuArray, org.json.JSONArray tablesArray,
                              org.json.JSONArray ordersArray) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("menu_items", null, null);
            for (int i = 0; i < menuArray.length(); i++) {
                org.json.JSONObject o = menuArray.getJSONObject(i);
                ContentValues values = new ContentValues();
                values.put("id", o.getLong("id"));
                values.put("name", o.getString("name"));
                values.put("description", o.optString("description", ""));
                values.put("quantity", o.getDouble("quantity"));
                values.put("unit", o.getString("unit"));
                values.put("category", o.getString("category"));
                values.put("category_text", o.optString("categoryText", ""));
                values.put("amount_text", o.optString("amountText", ""));
                values.put("section", o.optString("section",
                        MenuSection.fromCategory(MenuCategory.fromString(o.getString("category"))).name()));
                values.put("is_complex", o.optBoolean("complex", false) ? 1 : 0);
                values.put("price", o.getDouble("price"));
                db.insertWithOnConflict("menu_items", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }

            Set<Long> syncedTableIds = new HashSet<>();
            for (int i = 0; i < tablesArray.length(); i++) {
                org.json.JSONObject o = tablesArray.getJSONObject(i);
                long tableId = o.getLong("id");
                syncedTableIds.add(tableId);

                String syncedLabel = o.optString("label", RestaurantTable.DEFAULT_LABEL).trim();
                if (syncedLabel.isEmpty()) {
                    syncedLabel = RestaurantTable.DEFAULT_LABEL;
                }

                ContentValues values = new ContentValues();
                values.put("number", o.getInt("number"));
                values.put("label", syncedLabel);
                values.put("description", o.optString("description", ""));
                values.put("status", o.getString("status"));
                if (o.has("openOrderNumber")) {
                    values.put("open_order_number", o.optInt("openOrderNumber", 0));
                }

                if (tableExistsById(db, tableId)) {
                    db.update("tables", values, "id=?", new String[]{String.valueOf(tableId)});
                } else {
                    values.put("id", tableId);
                    db.insert("tables", null, values);
                }
            }
            deleteTablesNotIn(db, syncedTableIds);

            applyOrdersFromSync(db, ordersArray, syncedTableIds);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private boolean tableExistsById(SQLiteDatabase db, long tableId) {
        Cursor cursor = db.rawQuery(
                "SELECT 1 FROM tables WHERE id=? LIMIT 1",
                new String[]{String.valueOf(tableId)});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private void deleteTablesNotIn(SQLiteDatabase db, Set<Long> keepIds) {
        Cursor cursor = db.rawQuery("SELECT id FROM tables", null);
        List<Long> toDelete = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                if (!keepIds.contains(id)) {
                    toDelete.add(id);
                }
            }
        } finally {
            cursor.close();
        }
        for (Long id : toDelete) {
            db.delete("order_lines", "table_id=?", new String[]{String.valueOf(id)});
            db.delete("tables", "id=?", new String[]{String.valueOf(id)});
        }
    }

    private RestaurantTable findTableById(SQLiteDatabase db, long tableId) {
        Cursor cursor = db.rawQuery(
                "SELECT id, number, description, status, open_order_number, label FROM tables WHERE id=?",
                new String[]{String.valueOf(tableId)});
        try {
            if (cursor.moveToFirst()) {
                return mapTable(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private RestaurantTable findTableByNumber(SQLiteDatabase db, int number) {
        Cursor cursor = db.rawQuery(
                "SELECT id, number, description, status, open_order_number, label FROM tables WHERE number=?",
                new String[]{String.valueOf(number)});
        try {
            if (cursor.moveToFirst()) {
                return mapTable(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private void applyOrdersFromSync(SQLiteDatabase db, org.json.JSONArray ordersArray,
                                     Set<Long> syncedTableIds) throws Exception {
        Set<Long> tablesWithOrders = new HashSet<>();
        if (ordersArray != null) {
            for (int i = 0; i < ordersArray.length(); i++) {
                org.json.JSONObject order = ordersArray.getJSONObject(i);
                RestaurantTable table = null;
                long tableId = order.optLong("tableId", 0);
                if (tableId > 0) {
                    table = findTableById(db, tableId);
                }
                if (table == null && order.has("tableNumber")) {
                    table = findTableByNumber(db, order.getInt("tableNumber"));
                }
                if (table == null) {
                    continue;
                }
                tablesWithOrders.add(table.getId());
                int openOrderNumber = order.optInt("openOrderNumber", 0);
                if (openOrderNumber > 0) {
                    ContentValues tableValues = new ContentValues();
                    tableValues.put("open_order_number", openOrderNumber);
                    db.update("tables", tableValues, "id=?", new String[]{String.valueOf(table.getId())});
                }
                org.json.JSONArray linesArray = order.getJSONArray("lines");
                db.delete("order_lines", "table_id=?", new String[]{String.valueOf(table.getId())});
                for (int j = 0; j < linesArray.length(); j++) {
                    org.json.JSONObject o = linesArray.getJSONObject(j);
                    ContentValues values = new ContentValues();
                    values.put("table_id", table.getId());
                    values.put("menu_item_id", o.optLong("menuItemId", 0));
                    values.put("name", o.getString("name"));
                    values.put("item_quantity", o.getDouble("itemQuantity"));
                    values.put("unit", o.getString("unit"));
                    values.put("category", o.getString("category"));
                    values.put("price", o.getDouble("price"));
                    values.put("count", o.getInt("count"));
                    values.put("printed_count", o.optInt("printedCount", 0));
                    db.insert("order_lines", null, values);
                }
            }
        }
        for (Long tableId : syncedTableIds) {
            if (!tablesWithOrders.contains(tableId)) {
                db.delete("order_lines", "table_id=?", new String[]{String.valueOf(tableId)});
            }
        }
    }

    public void applySyncData(org.json.JSONArray menuArray, org.json.JSONArray tablesArray) throws Exception {
        applySyncData(menuArray, tablesArray, null);
    }
}
