package com.example.deviceinfo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.deviceinfo.db.DatabaseHelper;
import com.example.deviceinfo.model.MenuCategoryGroups;
import com.example.deviceinfo.util.MenuPickRowHelper;
import com.example.deviceinfo.model.MenuItem;
import com.example.deviceinfo.model.MenuSection;
import com.example.deviceinfo.model.OrderLine;
import com.example.deviceinfo.model.RestaurantTable;
import com.example.deviceinfo.model.TableStatus;
import com.example.deviceinfo.util.OrderEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MenuPickActivity extends AppCompatActivity {

    public static final String EXTRA_TABLE_ID = "table_id";
    public static final String EXTRA_SECTION = "section";
    public static final String EXTRA_HAS_COMPLEX_FILTER = "has_complex_filter";
    public static final String EXTRA_COMPLEX_ONLY = "complex_only";

    private DatabaseHelper db;
    private long tableId;
    private MenuSection section;
    private Boolean complexOnly;
    private List<MenuItem> items = new ArrayList<>();
    private LinearLayout llCategories;
    private ScrollView svPick;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_pick);

        db = DatabaseHelper.getInstance(this);
        tableId = getIntent().getLongExtra(EXTRA_TABLE_ID, -1);
        section = MenuSection.fromString(getIntent().getStringExtra(EXTRA_SECTION));
        if (getIntent().getBooleanExtra(EXTRA_HAS_COMPLEX_FILTER, false)) {
            complexOnly = getIntent().getBooleanExtra(EXTRA_COMPLEX_ONLY, false);
        } else {
            complexOnly = null;
        }

        if (tableId <= 0) {
            finish();
            return;
        }

        TextView tvTitle = (TextView) findViewById(R.id.tv_menu_pick_title);
        tvTitle.setText(buildTitle());

        tvEmpty = (TextView) findViewById(R.id.tv_menu_pick_empty);
        svPick = (ScrollView) findViewById(R.id.sv_menu_pick);
        llCategories = (LinearLayout) findViewById(R.id.ll_menu_categories);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadItems();
    }

    private String buildTitle() {
        if (section == MenuSection.LUNCH && complexOnly != null) {
            return getString(complexOnly ? R.string.lunch_complex : R.string.lunch_full_list);
        }
        return section.getDisplayName();
    }

    private void reloadItems() {
        items = db.getMenuItemsBySection(section, complexOnly);
        rebuildCategoryUi();
        boolean empty = items.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        svPick.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private Map<Long, Integer> currentCountsByMenuId() {
        Map<Long, Integer> counts = new HashMap<>();
        for (OrderLine line : db.getOrderLines(tableId)) {
            if (line.getMenuItemId() > 0) {
                counts.put(line.getMenuItemId(), line.getCount());
            }
        }
        return counts;
    }

    private void rebuildCategoryUi() {
        llCategories.removeAllViews();
        Map<Long, Integer> counts = currentCountsByMenuId();
        Map<String, List<MenuItem>> groups = MenuCategoryGroups.groupByCategoryText(items);
        boolean firstExpanded = true;
        for (Map.Entry<String, List<MenuItem>> entry : groups.entrySet()) {
            addCategoryBlock(entry.getKey(), entry.getValue(), firstExpanded, counts);
            firstExpanded = false;
        }
    }

    private void addCategoryBlock(String categoryName, List<MenuItem> groupItems, boolean expanded,
                                  Map<Long, Integer> counts) {
        Button btnHeader = new Button(new ContextThemeWrapper(this, R.style.SecondaryButton), null, 0);
        btnHeader.setAllCaps(false);
        btnHeader.setText(categoryName);

        final LinearLayout itemList = new LinearLayout(this);
        itemList.setOrientation(LinearLayout.VERTICAL);
        itemList.setBackgroundResource(R.drawable.bg_tile);
        int pad = dp(4);
        itemList.setPadding(pad, pad, pad, pad);
        itemList.setVisibility(expanded ? View.VISIBLE : View.GONE);

        for (final MenuItem item : groupItems) {
            final View row = getLayoutInflater().inflate(R.layout.item_menu_pick, itemList, false);
            row.setBackgroundResource(R.drawable.bg_pick_option);
            Integer countObj = counts.get(item.getId());
            int count = countObj == null ? 0 : countObj;
            MenuPickRowHelper.bind(row, item, buildItemDetails(item), count,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            addMenuItemToCheck(item, row);
                        }
                    },
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            decreaseMenuItem(item, row);
                        }
                    });
            itemList.addView(row);
        }

        btnHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCategory(itemList);
            }
        });

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (llCategories.getChildCount() > 0) {
            headerLp.topMargin = dp(8);
        }
        llCategories.addView(btnHeader, headerLp);
        llCategories.addView(itemList);
    }

    private String buildItemDetails(MenuItem item) {
        String amount = item.formatAmount();
        String price = String.format(Locale.getDefault(), "%.2f", item.getPrice()) + " руб.";
        if (amount.length() > 0) {
            return amount + " · " + price;
        }
        return price;
    }

    private void toggleCategory(View list) {
        list.setVisibility(list.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private void addMenuItemToCheck(MenuItem menuItem, View row) {
        List<OrderLine> orderLines = db.getOrderLines(tableId);
        for (OrderLine existing : orderLines) {
            if (existing.getMenuItemId() == menuItem.getId()) {
                existing.setCount(existing.getCount() + 1);
                db.updateOrderLine(existing);
                MenuPickRowHelper.updateCount(row, existing.getCount());
                afterChange(menuItem.getName());
                return;
            }
        }

        OrderLine line = new OrderLine();
        line.setTableId(tableId);
        line.setMenuItemId(menuItem.getId());
        line.setName(menuItem.getName());
        applyMenuAmount(line, menuItem);
        line.setCategory(menuItem.getCategory());
        line.setPrice(menuItem.getPrice());
        line.setCount(1);
        db.insertOrderLine(line);
        MenuPickRowHelper.updateCount(row, 1);
        afterChange(menuItem.getName());
    }

    private void decreaseMenuItem(MenuItem menuItem, View row) {
        List<OrderLine> orderLines = db.getOrderLines(tableId);
        for (OrderLine existing : orderLines) {
            if (existing.getMenuItemId() == menuItem.getId()) {
                int next = existing.getCount() - 1;
                if (next <= 0) {
                    db.deleteOrderLine(existing.getId());
                    MenuPickRowHelper.updateCount(row, 0);
                } else {
                    existing.setCount(next);
                    db.updateOrderLine(existing);
                    MenuPickRowHelper.updateCount(row, next);
                }
                afterChange(null);
                return;
            }
        }
    }

    private void applyMenuAmount(OrderLine line, MenuItem menuItem) {
        String amountText = menuItem.getAmountText();
        if (amountText != null && amountText.trim().length() > 0) {
            line.setItemQuantity(0);
            line.setUnit(amountText.trim());
        } else {
            line.setItemQuantity(menuItem.getQuantity());
            line.setUnit(menuItem.getUnit());
        }
    }

    private void afterChange(String addedName) {
        List<OrderLine> orderLines = db.getOrderLines(tableId);
        RestaurantTable table = db.getTable(tableId);
        if (table != null) {
            if (orderLines.isEmpty() && table.getStatus() == TableStatus.BUSY) {
                table.setStatus(TableStatus.FREE);
                db.updateTable(table);
            } else if (!orderLines.isEmpty() && table.getStatus() == TableStatus.FREE) {
                table.setStatus(TableStatus.BUSY);
                db.updateTable(table);
            }
        }
        OrderEvents.notifyChanged(this);
        if (addedName != null) {
            Toast.makeText(this, getString(R.string.menu_item_added_named, addedName),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
