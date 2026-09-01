package com.grinchcafe.waiter;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.grinchcafe.waiter.db.DatabaseHelper;
import com.grinchcafe.waiter.model.RestaurantTable;
import com.grinchcafe.waiter.model.TableStatus;
import com.grinchcafe.waiter.net.ServerConfig;
import com.grinchcafe.waiter.sync.SyncManager;
import com.grinchcafe.waiter.util.ErrorLogHelper;
import com.grinchcafe.waiter.util.UserFacingErrors;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SyncManager.Listener {

    private DatabaseHelper db;
    private TableAdapter adapter;
    private GridLayoutManager tableLayoutManager;
    private List<RestaurantTable> tables = new ArrayList<>();
    private LinearLayout panelSetup;
    private LinearLayout panelConnected;
    private EditText etServerHost;
    private TextView tvServerAddress;
    private SyncManager syncManager;
    private boolean expectManualSyncResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = DatabaseHelper.getInstance(this);
        db.ensureDefaultTables();
        db.ensureSampleMenu();
        syncManager = SyncManager.getInstance(this);

        panelSetup = (LinearLayout) findViewById(R.id.panel_setup);
        panelConnected = (LinearLayout) findViewById(R.id.panel_connected);
        etServerHost = (EditText) findViewById(R.id.et_server_host);
        tvServerAddress = (TextView) findViewById(R.id.tv_server_address);

        RecyclerView rvTables = (RecyclerView) findViewById(R.id.rv_tables);
        tableLayoutManager = new GridLayoutManager(this, getTableSpanCount());
        rvTables.setLayoutManager(tableLayoutManager);
        rvTables.setHasFixedSize(true);
        adapter = new TableAdapter();
        rvTables.setAdapter(adapter);

        findViewById(R.id.btn_save_server).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveInitialConfig();
            }
        });

        findViewById(R.id.btn_sync).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                expectManualSyncResult = true;
                syncManager.syncManual();
            }
        });

        findViewById(R.id.btn_config).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfigDialog();
            }
        });

        updateConnectionUi();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (tableLayoutManager != null) {
            tableLayoutManager.setSpanCount(getTableSpanCount());
        }
    }

    private int getTableSpanCount() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int widthDp = (int) (metrics.widthPixels / metrics.density);
        if (widthDp >= 720) {
            return 3;
        }
        if (widthDp >= 480) {
            return 2;
        }
        return 2;
    }

    @Override
    protected void onStart() {
        super.onStart();
        syncManager.addListener(this);
    }

    @Override
    protected void onStop() {
        syncManager.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTables();
    }

    private void updateConnectionUi() {
        if (ServerConfig.isConfigured(this)) {
            panelSetup.setVisibility(View.GONE);
            panelConnected.setVisibility(View.VISIBLE);
            tvServerAddress.setText(getString(R.string.connected_to, ServerConfig.getDisplayAddress(this)));
        } else {
            panelSetup.setVisibility(View.VISIBLE);
            panelConnected.setVisibility(View.GONE);
            etServerHost.setText(ServerConfig.getHost(this));
        }
    }

    private void saveInitialConfig() {
        String host = etServerHost.getText().toString().trim();
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(this, R.string.network_ip_required, Toast.LENGTH_SHORT).show();
            return;
        }
        ServerConfig.markConfigured(this, host, ServerConfig.DEFAULT_PORT);
        updateConnectionUi();
        syncManager.start();
        expectManualSyncResult = true;
        syncManager.syncManual();
        Toast.makeText(this, R.string.server_saved, Toast.LENGTH_SHORT).show();
    }

    private void showConfigDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_server_config, null);
        final EditText etHost = (EditText) view.findViewById(R.id.et_config_host);
        etHost.setText(ServerConfig.getHost(this));

        new AlertDialog.Builder(this)
                .setTitle(R.string.configuration)
                .setView(view)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String host = etHost.getText().toString().trim();
                        if (TextUtils.isEmpty(host)) {
                            Toast.makeText(MainActivity.this, R.string.network_ip_required,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ServerConfig.markConfigured(MainActivity.this, host, ServerConfig.DEFAULT_PORT);
                        updateConnectionUi();
                        expectManualSyncResult = true;
                        syncManager.syncManual();
                        Toast.makeText(MainActivity.this, R.string.server_saved, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadTables() {
        tables = db.getAllTables();
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onSyncSuccess() {
        loadTables();
        if (expectManualSyncResult) {
            expectManualSyncResult = false;
            int menuCount = db.getAllMenuItems().size();
            Toast.makeText(this, getString(R.string.sync_success_menu, menuCount),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSyncFailed(String message) {
        expectManualSyncResult = false;
        ErrorLogHelper.log(this, "Синхронизация", message);
        Toast.makeText(this, getString(R.string.sync_failed,
                UserFacingErrors.format(this, message)), Toast.LENGTH_SHORT).show();
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

    private class TableAdapter extends RecyclerView.Adapter<TableAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_table_waiter, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.bind(tables.get(position));
        }

        @Override
        public int getItemCount() {
            return tables.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final View statusIndicator;
            private final TextView tvLabel;
            private final TextView tvNumber;
            private final TextView tvDescription;
            private final TextView tvStatus;

            ViewHolder(View itemView) {
                super(itemView);
                statusIndicator = itemView.findViewById(R.id.view_status_indicator);
                tvLabel = (TextView) itemView.findViewById(R.id.tv_table_label);
                tvNumber = (TextView) itemView.findViewById(R.id.tv_table_number);
                tvDescription = (TextView) itemView.findViewById(R.id.tv_table_description);
                tvStatus = (TextView) itemView.findViewById(R.id.tv_table_status);
            }

            void bind(final RestaurantTable table) {
                int color = statusColor(table.getStatus());
                tvLabel.setText(table.getDisplayLabel());
                tvNumber.setText(getString(R.string.table_number_short, table.getNumber()));
                String description = table.getDescription();
                boolean hasDescription = description != null && description.trim().length() > 0;
                if (hasDescription) {
                    tvDescription.setVisibility(View.VISIBLE);
                    tvDescription.setText(description.trim());
                } else {
                    tvDescription.setVisibility(View.GONE);
                }
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
            }
        }
    }
}
