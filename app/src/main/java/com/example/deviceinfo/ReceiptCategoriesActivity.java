package com.example.deviceinfo;

import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.example.deviceinfo.db.DatabaseHelper;
import com.example.deviceinfo.model.MenuCategory;
import com.example.deviceinfo.util.ReceiptCategoryConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReceiptCategoriesActivity extends AppCompatActivity {

    private static final ReceiptCategoryConfig.Target[] TARGETS = {
            ReceiptCategoryConfig.Target.BAR,
            ReceiptCategoryConfig.Target.KITCHEN
    };

    private DatabaseHelper db;
    private List<MenuCategory> categories = new ArrayList<>();
    private LinearLayout layoutCategories;
    private Spinner spinnerTarget;
    private ReceiptCategoryConfig.Target currentTarget = ReceiptCategoryConfig.Target.BAR;
    private boolean loadingCheckboxes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_categories);

        db = DatabaseHelper.getInstance(this);
        layoutCategories = (LinearLayout) findViewById(R.id.layout_categories);
        spinnerTarget = (Spinner) findViewById(R.id.spinner_receipt_target);

        categories = db.getDistinctMenuCategories();
        ReceiptCategoryConfig.sortCategories(categories);

        spinnerTarget.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        getString(R.string.receipt_target_bar),
                        getString(R.string.receipt_target_kitchen)
                }));
        spinnerTarget.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentTarget = TARGETS[position];
                showCategoriesForTarget(currentTarget);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        findViewById(R.id.btn_save_receipt_categories).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentSelection();
            }
        });

        findViewById(R.id.btn_reset_receipt_categories).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ReceiptCategoryConfig.resetToDefaults(ReceiptCategoriesActivity.this, currentTarget);
                showCategoriesForTarget(currentTarget);
                Toast.makeText(ReceiptCategoriesActivity.this,
                        R.string.receipt_categories_reset, Toast.LENGTH_SHORT).show();
            }
        });

        showCategoriesForTarget(currentTarget);
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<MenuCategory> latest = db.getDistinctMenuCategories();
        if (!latest.equals(categories)) {
            categories = latest;
            showCategoriesForTarget(currentTarget);
        }
    }

    private void showCategoriesForTarget(ReceiptCategoryConfig.Target target) {
        layoutCategories.removeAllViews();
        loadingCheckboxes = true;

        Set<MenuCategory> enabled = ReceiptCategoryConfig.getEnabledCategories(
                this, target, categories);

        for (MenuCategory category : categories) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(category.getDisplayName());
            checkBox.setTag(category);
            checkBox.setChecked(enabled.contains(category));
            checkBox.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
            checkBox.setPadding(0, 12, 0, 12);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (!loadingCheckboxes) {
                        saveCurrentSelection();
                    }
                }
            });
            layoutCategories.addView(checkBox);
        }

        loadingCheckboxes = false;
    }

    private void saveCurrentSelection() {
        Set<MenuCategory> enabled = new HashSet<>();
        for (int i = 0; i < layoutCategories.getChildCount(); i++) {
            View child = layoutCategories.getChildAt(i);
            if (child instanceof CheckBox) {
                CheckBox checkBox = (CheckBox) child;
                if (checkBox.isChecked()) {
                    enabled.add((MenuCategory) checkBox.getTag());
                }
            }
        }
        ReceiptCategoryConfig.save(this, currentTarget, enabled, categories);
        if (!loadingCheckboxes) {
            Toast.makeText(this, R.string.receipt_categories_saved, Toast.LENGTH_SHORT).show();
        }
    }
}
