package com.example.deviceinfo;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ContextThemeWrapper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.deviceinfo.db.DatabaseHelper;
import com.example.deviceinfo.model.MenuCategory;
import com.example.deviceinfo.model.MenuItem;
import com.example.deviceinfo.model.MenuSection;
import com.example.deviceinfo.util.MenuFileImporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MenuAdminActivity extends AppCompatActivity {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_LUNCH = 1;
    private static final int FILTER_MAIN = 2;
    private static final int FILTER_BAR = 3;
    private static final int REQUEST_IMPORT_FILE = 2101;
    private static final int REQUEST_IMPORT_PERMISSION = 2102;

    private DatabaseHelper db;
    private List<MenuItem> allMenuItems = new ArrayList<>();
    private final Set<String> expandedKeys = new HashSet<>();
    private LinearLayout llCategories;
    private ScrollView svMenu;
    private Spinner spinnerFilter;
    private TextView tvEmpty;
    private int currentFilter = FILTER_MAIN;
    private MenuSection pendingImportSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_admin);

        db = DatabaseHelper.getInstance(this);
        svMenu = (ScrollView) findViewById(R.id.sv_menu_items);
        llCategories = (LinearLayout) findViewById(R.id.ll_menu_categories);
        tvEmpty = (TextView) findViewById(R.id.tv_menu_admin_empty);

        spinnerFilter = (Spinner) findViewById(R.id.spinner_menu_filter);
        spinnerFilter.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        getString(R.string.menu_filter_all),
                        getString(R.string.menu_section_lunch),
                        getString(R.string.menu_section_main),
                        getString(R.string.menu_section_bar)
                }));
        spinnerFilter.setSelection(FILTER_MAIN, false);
        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFilter = position;
                applyFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        final EditText etComplexPrice = (EditText) findViewById(R.id.et_complex_price);
        etComplexPrice.setText(String.valueOf(db.getComplexPrice()));
        findViewById(R.id.btn_save_complex_price).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double price = parseDouble(etComplexPrice.getText().toString(), -1);
                if (price < 0) {
                    Toast.makeText(MenuAdminActivity.this, R.string.complex_price_invalid, Toast.LENGTH_SHORT).show();
                    return;
                }
                db.setComplexPrice(price);
                Toast.makeText(MenuAdminActivity.this, R.string.complex_price_saved, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_add_menu_item).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenuItemDialog(null);
            }
        });

        findViewById(R.id.btn_import_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImportSectionDialog();
            }
        });

        findViewById(R.id.btn_clear_all_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearAllMenu();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadMenu();
    }

    private void reloadMenu() {
        allMenuItems = db.getAllMenuItems();
        applyFilter();
    }

    private void applyFilter() {
        List<MenuItem> filtered = new ArrayList<>();
        for (MenuItem item : allMenuItems) {
            if (item.isComplex()) {
                continue;
            }
            if (currentFilter == FILTER_ALL) {
                filtered.add(item);
            } else if (currentFilter == FILTER_LUNCH && item.getSection() == MenuSection.LUNCH) {
                filtered.add(item);
            } else if (currentFilter == FILTER_MAIN && item.getSection() == MenuSection.MAIN) {
                filtered.add(item);
            } else if (currentFilter == FILTER_BAR && item.getSection() == MenuSection.BAR) {
                filtered.add(item);
            }
        }
        rebuildCategoryUi(filtered);
        boolean empty = filtered.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        svMenu.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void rebuildCategoryUi(List<MenuItem> filtered) {
        Collections.sort(filtered, new Comparator<MenuItem>() {
            @Override
            public int compare(MenuItem a, MenuItem b) {
                int sectionCompare = a.getSection().ordinal() - b.getSection().ordinal();
                if (sectionCompare != 0) {
                    return sectionCompare;
                }
                int priority = Integer.compare(
                        a.getCategory().getPrintPriority(), b.getCategory().getPrintPriority());
                if (priority != 0) {
                    return priority;
                }
                int title = a.getCategoryText().compareToIgnoreCase(b.getCategoryText());
                if (title != 0) {
                    return title;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        LinkedHashMap<String, List<MenuItem>> groups = new LinkedHashMap<>();
        for (MenuItem item : filtered) {
            String key = groupKey(item);
            List<MenuItem> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
            }
            group.add(item);
        }

        llCategories.removeAllViews();
        boolean anyExpanded = false;
        for (String key : groups.keySet()) {
            if (expandedKeys.contains(key)) {
                anyExpanded = true;
                break;
            }
        }
        boolean expandFirst = !anyExpanded;
        for (Map.Entry<String, List<MenuItem>> entry : groups.entrySet()) {
            String key = entry.getKey();
            boolean expanded = expandFirst || expandedKeys.contains(key);
            if (expandFirst) {
                expandedKeys.add(key);
                expandFirst = false;
            }
            addCategoryBlock(key, groupTitle(entry.getValue().get(0)), entry.getValue(), expanded);
        }
    }

    private void addCategoryBlock(final String key, String title, List<MenuItem> groupItems,
                                  boolean expanded) {
        Button btnHeader = new Button(new ContextThemeWrapper(this, R.style.SecondaryButton), null, 0);
        btnHeader.setAllCaps(false);
        btnHeader.setText(title + "  (" + groupItems.size() + ")");

        final LinearLayout itemList = new LinearLayout(this);
        itemList.setOrientation(LinearLayout.VERTICAL);
        itemList.setBackgroundResource(R.drawable.bg_tile);
        int pad = dp(4);
        itemList.setPadding(pad, pad, pad, pad);
        itemList.setVisibility(expanded ? View.VISIBLE : View.GONE);

        for (final MenuItem item : groupItems) {
            View row = getLayoutInflater().inflate(R.layout.item_menu, itemList, false);
            TextView tvName = (TextView) row.findViewById(R.id.tv_menu_name);
            TextView tvDetails = (TextView) row.findViewById(R.id.tv_menu_details);
            tvName.setText(item.getName());
            tvDetails.setText(item.formatAmount() + " · "
                    + String.format(Locale.getDefault(), "%.2f", item.getPrice()) + " руб.");
            row.findViewById(R.id.btn_edit_menu).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showMenuItemDialog(item);
                }
            });
            row.findViewById(R.id.btn_delete_menu).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete(item);
                }
            });
            itemList.addView(row);
        }

        btnHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean nowVisible = itemList.getVisibility() != View.VISIBLE;
                itemList.setVisibility(nowVisible ? View.VISIBLE : View.GONE);
                if (nowVisible) {
                    expandedKeys.add(key);
                } else {
                    expandedKeys.remove(key);
                }
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

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private String groupKey(MenuItem item) {
        return item.getSection().name() + "|" + item.getCategoryText();
    }

    private String groupTitle(MenuItem item) {
        if (currentFilter == FILTER_ALL) {
            return item.getSection().getDisplayName() + " · " + item.getCategoryText();
        }
        return item.getCategoryText();
    }

    private void showMenuItemDialog(final MenuItem existing) {
        View view = getLayoutInflater().inflate(R.layout.dialog_menu_item, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_menu_name);
        final EditText etDescription = (EditText) view.findViewById(R.id.et_menu_description);
        final EditText etQuantity = (EditText) view.findViewById(R.id.et_menu_quantity);
        final EditText etPrice = (EditText) view.findViewById(R.id.et_menu_price);
        final Spinner spinnerUnit = (Spinner) view.findViewById(R.id.spinner_menu_unit);
        final Spinner spinnerSection = (Spinner) view.findViewById(R.id.spinner_menu_section);
        final Spinner spinnerCategory = (Spinner) view.findViewById(R.id.spinner_menu_category);

        spinnerUnit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"шт", "г", "мл", "л"}));
        spinnerSection.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                MenuSection.editableDisplayNames()));

        spinnerSection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                MenuSection section = MenuSection.editableSections()[position];
                bindCategorySpinner(spinnerCategory, section, existing);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (existing != null) {
            etName.setText(existing.getName());
            etDescription.setText(existing.getDescription());
            etQuantity.setText(String.valueOf(existing.getQuantity()));
            etPrice.setText(String.valueOf(existing.getPrice()));
            spinnerUnit.setSelection(unitIndex(existing.getUnit()));
            spinnerSection.setSelection(MenuSection.editableIndex(existing.getSection()));
        } else {
            etQuantity.setText("1");
            etPrice.setText("0");
            int defaultSection = currentFilter == FILTER_LUNCH
                    ? MenuSection.editableIndex(MenuSection.LUNCH)
                    : currentFilter == FILTER_BAR
                    ? MenuSection.editableIndex(MenuSection.BAR)
                    : MenuSection.editableIndex(MenuSection.MAIN);
            spinnerSection.setSelection(defaultSection);
        }

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.add_menu_item : R.string.edit_menu_item)
                .setView(view)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveMenuItem(existing, etName, etDescription, etQuantity, etPrice,
                                spinnerUnit, spinnerSection, spinnerCategory);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void bindCategorySpinner(Spinner spinnerCategory, MenuSection section, MenuItem existing) {
        if (section == MenuSection.LUNCH) {
            spinnerCategory.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, MenuCategory.lunchDisplayNames()));
            if (existing != null && existing.getSection() == MenuSection.LUNCH) {
                spinnerCategory.setSelection(MenuCategory.lunchIndex(existing.getCategory()));
            } else {
                spinnerCategory.setSelection(0);
            }
        } else if (section == MenuSection.BAR) {
            spinnerCategory.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, MenuCategory.barDisplayNames()));
            if (existing != null && existing.getSection() == MenuSection.BAR) {
                spinnerCategory.setSelection(MenuCategory.barIndex(existing.getCategory()));
            } else {
                spinnerCategory.setSelection(0);
            }
        } else {
            spinnerCategory.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, MenuCategory.mainMenuDisplayNames()));
            if (existing != null && existing.getSection() == MenuSection.MAIN) {
                spinnerCategory.setSelection(MenuCategory.mainMenuIndex(existing.getCategory()));
            } else {
                spinnerCategory.setSelection(0);
            }
        }
    }

    private void saveMenuItem(MenuItem existing, EditText etName, EditText etDescription,
                              EditText etQuantity, EditText etPrice,
                              Spinner spinnerUnit, Spinner spinnerSection,
                              Spinner spinnerCategory) {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.menu_name_required, Toast.LENGTH_SHORT).show();
            return;
        }

        MenuItem item = existing != null ? existing : new MenuItem();
        item.setName(name);
        item.setDescription(etDescription.getText().toString().trim());
        item.setQuantity(parseDouble(etQuantity.getText().toString(), 1));
        item.setUnit(spinnerUnit.getSelectedItem().toString());
        MenuSection section = MenuSection.editableSections()[spinnerSection.getSelectedItemPosition()];
        item.setSection(section);
        if (section == MenuSection.LUNCH) {
            item.setCategory(MenuCategory.lunchCategories()[spinnerCategory.getSelectedItemPosition()]);
        } else if (section == MenuSection.BAR) {
            item.setCategory(MenuCategory.barCategories()[spinnerCategory.getSelectedItemPosition()]);
        } else {
            item.setCategory(MenuCategory.mainMenuCategories()[spinnerCategory.getSelectedItemPosition()]);
        }
        item.setComplex(false);
        item.setPrice(parseDouble(etPrice.getText().toString(), 0));

        if (existing == null) {
            db.insertMenuItem(item);
        } else {
            db.updateMenuItem(item);
        }
        reloadMenu();
    }

    private void confirmDelete(final MenuItem item) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_delete_menu, item.getName()))
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        db.deleteMenuItem(item.getId());
                        reloadMenu();
                        Toast.makeText(MenuAdminActivity.this, R.string.menu_deleted,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmClearAllMenu() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.clear_all_menu_confirm)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        db.clearAllMenuItems();
                        reloadMenu();
                        Toast.makeText(MenuAdminActivity.this,
                                R.string.clear_all_menu_success,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showImportSectionDialog() {
        final MenuSection[] sections = MenuSection.values();
        new AlertDialog.Builder(this)
                .setTitle(R.string.import_menu_choose_section)
                .setItems(MenuSection.displayNames(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pendingImportSection = sections[which];
                        startFilePicker();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startFilePicker() {
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_IMPORT_PERMISSION);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (Build.VERSION.SDK_INT >= 19) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "text/csv",
                    "text/comma-separated-values",
                    "application/octet-stream"
            });
        }
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.import_menu_file)),
                    REQUEST_IMPORT_FILE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.import_menu_pick_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_IMPORT_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startFilePicker();
        } else if (requestCode == REQUEST_IMPORT_PERMISSION) {
            Toast.makeText(this, R.string.import_menu_permission, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_FILE || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null || pendingImportSection == null) {
            Toast.makeText(this, R.string.import_menu_pick_failed, Toast.LENGTH_LONG).show();
            return;
        }
        importFromUri(uri, pendingImportSection);
    }

    private void importFromUri(final Uri uri, final MenuSection section) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File temp = null;
                try {
                    temp = copyUriToCache(uri);
                    final MenuFileImporter.Result result = MenuFileImporter.importFile(temp, section);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) {
                                return;
                            }
                            confirmImport(section, result.items);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing()) {
                                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                                Toast.makeText(MenuAdminActivity.this,
                                        getString(R.string.import_menu_failed, message),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } finally {
                    if (temp != null) {
                        temp.delete();
                    }
                }
            }
        }).start();
    }

    private void confirmImport(final MenuSection section, final List<MenuItem> items) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.import_menu_confirm, section.getDisplayName(), items.size()))
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        db.replaceMenuSection(section, items);
                        if (section == MenuSection.LUNCH) {
                            currentFilter = FILTER_LUNCH;
                            spinnerFilter.setSelection(FILTER_LUNCH);
                        } else if (section == MenuSection.BAR) {
                            currentFilter = FILTER_BAR;
                            spinnerFilter.setSelection(FILTER_BAR);
                        } else {
                            currentFilter = FILTER_MAIN;
                            spinnerFilter.setSelection(FILTER_MAIN);
                        }
                        reloadMenu();
                        Toast.makeText(MenuAdminActivity.this,
                                getString(R.string.import_menu_success, items.size()),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private File copyUriToCache(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IllegalArgumentException(getString(R.string.import_menu_pick_failed));
        }
        File out = new File(getCacheDir(), "menu_import.tmp");
        FileOutputStream fos = new FileOutputStream(out);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            try {
                fos.close();
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private int unitIndex(String unit) {
        String[] units = {"шт", "г", "мл", "л"};
        for (int i = 0; i < units.length; i++) {
            if (units[i].equals(unit)) {
                return i;
            }
        }
        return 0;
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
