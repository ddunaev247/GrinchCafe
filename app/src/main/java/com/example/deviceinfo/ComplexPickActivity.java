package com.example.deviceinfo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.deviceinfo.db.DatabaseHelper;
import com.example.deviceinfo.model.LunchComplex;
import com.example.deviceinfo.util.MenuPickRowHelper;
import com.example.deviceinfo.model.MenuCategory;
import com.example.deviceinfo.model.MenuItem;
import com.example.deviceinfo.model.MenuSection;
import com.example.deviceinfo.model.OrderLine;
import com.example.deviceinfo.model.RestaurantTable;
import com.example.deviceinfo.model.TableStatus;

import java.util.List;
import java.util.Locale;

public class ComplexPickActivity extends AppCompatActivity {

    public static final String EXTRA_TABLE_ID = "table_id";

    private DatabaseHelper db;
    private long tableId;
    private MenuItem selectedSoup;
    private MenuItem selectedHot;
    private MenuItem selectedSalad;
    private LinearLayout soupList;
    private LinearLayout hotList;
    private LinearLayout saladList;
    private Button btnSoup;
    private Button btnHot;
    private Button btnSalad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complex_pick);

        db = DatabaseHelper.getInstance(this);
        tableId = getIntent().getLongExtra(EXTRA_TABLE_ID, -1);
        if (tableId <= 0) {
            finish();
            return;
        }

        TextView tvPrice = (TextView) findViewById(R.id.tv_complex_price);
        tvPrice.setText(getString(R.string.complex_price_label,
                String.format(Locale.getDefault(), "%.2f", db.getComplexPrice())));

        btnSoup = (Button) findViewById(R.id.btn_slot_soup);
        btnHot = (Button) findViewById(R.id.btn_slot_hot);
        btnSalad = (Button) findViewById(R.id.btn_slot_salad);
        soupList = (LinearLayout) findViewById(R.id.ll_slot_soup);
        hotList = (LinearLayout) findViewById(R.id.ll_slot_hot);
        saladList = (LinearLayout) findViewById(R.id.ll_slot_salad);

        List<MenuItem> lunchItems = db.getMenuItemsBySection(MenuSection.LUNCH, false);
        fillSlot(soupList, LunchComplex.filter(lunchItems, LunchComplex.Slot.SOUP), LunchComplex.Slot.SOUP);
        fillSlot(hotList, LunchComplex.filter(lunchItems, LunchComplex.Slot.HOT), LunchComplex.Slot.HOT);
        fillSlot(saladList, LunchComplex.filter(lunchItems, LunchComplex.Slot.SALAD), LunchComplex.Slot.SALAD);

        btnSoup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSlot(soupList);
            }
        });
        btnHot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSlot(hotList);
            }
        });
        btnSalad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSlot(saladList);
            }
        });

        findViewById(R.id.btn_confirm_complex).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmComplex();
            }
        });

        soupList.setVisibility(View.VISIBLE);
        refreshHeaders();
    }

    private void toggleSlot(View list) {
        list.setVisibility(list.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void fillSlot(LinearLayout container, List<MenuItem> items, final LunchComplex.Slot slot) {
        container.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.complex_slot_empty);
            empty.setTextColor(getResources().getColor(R.color.textSecondary));
            empty.setPadding(14, 16, 14, 16);
            container.addView(empty);
            return;
        }
        for (final MenuItem item : items) {
            View row = getLayoutInflater().inflate(R.layout.item_menu_pick, container, false);
            row.setBackgroundResource(R.drawable.bg_pick_option);
            MenuPickRowHelper.bind(row, item, item.formatAmount(), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectItem(slot, item);
                }
            });
            row.setTag(item.getId());
            container.addView(row);
        }
    }

    private void selectItem(LunchComplex.Slot slot, MenuItem item) {
        switch (slot) {
            case SOUP:
                selectedSoup = item;
                highlight(soupList, item.getId());
                break;
            case HOT:
                selectedHot = item;
                highlight(hotList, item.getId());
                break;
            case SALAD:
                selectedSalad = item;
                highlight(saladList, item.getId());
                break;
        }
        refreshHeaders();
    }

    private void highlight(LinearLayout container, long selectedId) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof Long && ((Long) tag) == selectedId) {
                child.setBackgroundResource(R.drawable.bg_pick_option_selected);
            } else if (tag != null) {
                child.setBackgroundResource(R.drawable.bg_pick_option);
            }
        }
    }

    private void refreshHeaders() {
        btnSoup.setText(headerText(R.string.complex_slot_soup, selectedSoup));
        btnHot.setText(headerText(R.string.complex_slot_hot, selectedHot));
        btnSalad.setText(headerText(R.string.complex_slot_salad, selectedSalad));
    }

    private String headerText(int titleRes, MenuItem selected) {
        String title = getString(titleRes);
        if (selected == null) {
            return title;
        }
        return title + ": " + selected.getName();
    }

    private void confirmComplex() {
        if (selectedSoup == null || selectedHot == null || selectedSalad == null) {
            Toast.makeText(this, R.string.complex_need_all, Toast.LENGTH_SHORT).show();
            return;
        }

        String name = LunchComplex.formatCheckName(selectedSoup, selectedHot, selectedSalad);
        double price = db.getComplexPrice();
        List<OrderLine> orderLines = db.getOrderLines(tableId);
        for (OrderLine existing : orderLines) {
            if (LunchComplex.isComboLine(existing) && name.equals(existing.getName())) {
                existing.setCount(existing.getCount() + 1);
                existing.setPrice(price);
                db.updateOrderLine(existing);
                markTableBusy();
                Toast.makeText(this, R.string.menu_item_added, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        OrderLine line = new OrderLine();
        line.setTableId(tableId);
        line.setMenuItemId(LunchComplex.MENU_ITEM_ID);
        line.setName(name);
        line.setItemQuantity(1);
        line.setUnit("шт");
        line.setCategory(MenuCategory.MAIN);
        line.setPrice(price);
        line.setCount(1);
        db.insertOrderLine(line);
        markTableBusy();
        Toast.makeText(this, R.string.menu_item_added, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void markTableBusy() {
        RestaurantTable table = db.getTable(tableId);
        if (table != null && table.getStatus() == TableStatus.FREE) {
            table.setStatus(TableStatus.BUSY);
            db.updateTable(table);
        }
    }
}
