package com.example.deviceinfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.deviceinfo.db.DatabaseHelper;
import com.example.deviceinfo.model.OrderLine;
import com.example.deviceinfo.model.RestaurantTable;
import com.example.deviceinfo.model.TableStatus;
import com.example.deviceinfo.util.AppStateManager;
import com.example.deviceinfo.util.OrderEvents;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int VIEW_TYPE_TABLE = 0;
    private static final int VIEW_TYPE_ADD = 1;

    private DatabaseHelper db;
    private TableAdapter adapter;
    private GridLayoutManager layoutManager;
    private List<RestaurantTable> tables = new ArrayList<>();
    private boolean restoreDialogShown;

    private final BroadcastReceiver orderChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadTables();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = DatabaseHelper.getInstance(this);
        db.ensureDefaultTables();
        db.ensureSampleMenu();

        RecyclerView rvTables = (RecyclerView) findViewById(R.id.rv_tables);
        layoutManager = new GridLayoutManager(this, getTableSpanCount());
        rvTables.setLayoutManager(layoutManager);
        adapter = new TableAdapter();
        rvTables.setAdapter(adapter);

        findViewById(R.id.btn_menu_admin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, MenuAdminActivity.class));
            }
        });

        findViewById(R.id.btn_reports).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ReportsActivity.class));
            }
        });

        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        offerSessionRestore();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (layoutManager != null) {
            layoutManager.setSpanCount(getTableSpanCount());
        }
    }

    private int getTableSpanCount() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int widthDp = (int) (metrics.widthPixels / metrics.density);
        if (widthDp >= 1200) {
            return 5;
        }
        if (widthDp >= 900) {
            return 4;
        }
        return 3;
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                orderChangedReceiver, new IntentFilter(OrderEvents.ACTION_ORDER_CHANGED));
        loadTables();
    }

    @Override
    protected void onPause() {
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(orderChangedReceiver);
        } catch (Exception ignored) {
        }
        super.onPause();
    }

    private void loadTables() {
        tables = db.getAllTables();
        adapter.notifyDataSetChanged();
    }

    private void offerSessionRestore() {
        if (restoreDialogShown) {
            return;
        }

        final long lastTableId = AppStateManager.getLastTableId(this);
        if (lastTableId <= 0) {
            return;
        }

        List<OrderLine> lines = db.getOrderLines(lastTableId);
        if (lines.isEmpty()) {
            AppStateManager.clearLastTableId(this);
            return;
        }

        final RestaurantTable table = db.getTable(lastTableId);
        if (table == null) {
            AppStateManager.clearLastTableId(this);
            return;
        }

        restoreDialogShown = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.restore_session_title)
                .setMessage(getString(R.string.restore_session_message, table.getNumber()))
                .setPositiveButton(R.string.restore_session_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(MainActivity.this, TableActivity.class);
                        intent.putExtra(TableActivity.EXTRA_TABLE_ID, lastTableId);
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.restore_session_dismiss, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AppStateManager.clearLastTableId(MainActivity.this);
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void deleteTableById(long tableId) {
        db.deleteTable(tableId);
        if (AppStateManager.getLastTableId(this) == tableId) {
            AppStateManager.clearLastTableId(this);
        }
        OrderEvents.notifyChanged(this);
        loadTables();
    }

    private void showTableDialog(final RestaurantTable existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_table_edit, null);
        final EditText etLabel = (EditText) view.findViewById(R.id.et_table_label);
        final EditText etNumber = (EditText) view.findViewById(R.id.et_table_number);
        final EditText etDescription = (EditText) view.findViewById(R.id.et_table_description);
        final Spinner spinnerStatus = (Spinner) view.findViewById(R.id.spinner_table_status);
        spinnerStatus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                TableStatus.displayNames()));

        if (existing != null) {
            etLabel.setText(existing.getDisplayLabel());
            etNumber.setText(String.valueOf(existing.getNumber()));
            etDescription.setText(existing.getDescription());
            spinnerStatus.setSelection(existing.getStatus().ordinal());
        } else {
            etLabel.setText(RestaurantTable.DEFAULT_LABEL);
        }

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.add_table : R.string.edit_table)
                .setView(view)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String numberText = etNumber.getText().toString().trim();
                        if (numberText.isEmpty()) {
                            Toast.makeText(MainActivity.this, R.string.table_number_required,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        RestaurantTable table = existing != null ? existing : new RestaurantTable();
                        table.setLabel(etLabel.getText().toString().trim());
                        table.setNumber(Integer.parseInt(numberText));
                        table.setDescription(etDescription.getText().toString().trim());
                        table.setStatus(TableStatus.values()[spinnerStatus.getSelectedItemPosition()]);

                        if (existing == null) {
                            db.insertTable(table);
                        } else {
                            db.updateTable(table);
                        }
                        loadTables();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int statusColor(TableStatus status) {
        switch (status) {
            case BUSY:
                return ContextCompat.getColor(this, R.color.statusBusy);
            case RESERVED:
                return ContextCompat.getColor(this, R.color.statusReserved);
            case FREE:
            default:
                return ContextCompat.getColor(this, R.color.statusFree);
        }
    }

    private class TableAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return position < tables.size() ? VIEW_TYPE_TABLE : VIEW_TYPE_ADD;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_TYPE_ADD) {
                View view = inflater.inflate(R.layout.item_table_add, parent, false);
                return new AddViewHolder(view);
            }
            View view = inflater.inflate(R.layout.item_table, parent, false);
            return new TableViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof TableViewHolder) {
                ((TableViewHolder) holder).bind(tables.get(position));
            }
        }

        @Override
        public int getItemCount() {
            return tables.size() + 1;
        }

        class AddViewHolder extends RecyclerView.ViewHolder {
            AddViewHolder(View itemView) {
                super(itemView);
                itemView.findViewById(R.id.btn_add_table_tile).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showTableDialog(null);
                    }
                });
            }
        }

        class TableViewHolder extends RecyclerView.ViewHolder {
            private final View statusIndicator;
            private final TextView tvNumber;
            private final TextView tvDescription;
            private final TextView tvStatus;
            private final TextView btnEdit;
            private final TextView btnDelete;

            TableViewHolder(View itemView) {
                super(itemView);
                statusIndicator = itemView.findViewById(R.id.view_status_indicator);
                tvNumber = (TextView) itemView.findViewById(R.id.tv_table_number);
                tvDescription = (TextView) itemView.findViewById(R.id.tv_table_description);
                tvStatus = (TextView) itemView.findViewById(R.id.tv_table_status);
                btnEdit = (TextView) itemView.findViewById(R.id.btn_edit_table);
                btnDelete = (TextView) itemView.findViewById(R.id.btn_delete_table);
            }

            void bind(final RestaurantTable table) {
                int color = statusColor(table.getStatus());

                tvNumber.setText(table.formatCardTitle());
                tvDescription.setText(table.getDescription() == null || table.getDescription().isEmpty()
                        ? getString(R.string.no_description) : table.getDescription());
                tvStatus.setText(table.getStatus().getDisplayName());

                statusIndicator.setBackgroundColor(color);
                GradientDrawable badge = new GradientDrawable();
                badge.setCornerRadius(20f);
                badge.setColor(color);
                tvStatus.setBackground(badge);

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, TableActivity.class);
                        intent.putExtra(TableActivity.EXTRA_TABLE_ID, table.getId());
                        startActivity(intent);
                    }
                });

                btnEdit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showTableDialog(table);
                    }
                });

                btnDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage(R.string.confirm_delete_table)
                                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        deleteTableById(table.getId());
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                });
            }
        }
    }
}
