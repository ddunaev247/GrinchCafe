package com.grinchcafe.waiter;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.grinchcafe.waiter.db.DatabaseHelper;
import com.grinchcafe.waiter.model.OrderLine;
import com.grinchcafe.waiter.model.RestaurantTable;
import com.grinchcafe.waiter.model.TableStatus;
import com.grinchcafe.waiter.net.CafeClient;
import com.grinchcafe.waiter.net.ServerConfig;
import com.grinchcafe.waiter.sync.OrderSync;
import com.grinchcafe.waiter.sync.SyncManager;
import com.grinchcafe.waiter.util.AppStateManager;
import com.grinchcafe.waiter.util.ErrorLogHelper;
import com.grinchcafe.waiter.util.TableOrderHistoryBuilder;
import com.grinchcafe.waiter.util.UserFacingErrors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TableActivity extends AppCompatActivity implements SyncManager.Listener {

    public static final String EXTRA_TABLE_ID = "table_id";
    private static final String STATE_TABLE_ID = "state_table_id";

    private DatabaseHelper db;
    private SyncManager syncManager;
    private long tableId;
    private RestaurantTable table;
    private List<OrderLine> orderLines = new ArrayList<>();
    private OrderLineAdapter orderAdapter;
    private TextView tvTableInfo;
    private TextView tvTotal;
    private Spinner spinnerStatus;
    private boolean spinnerInitializing;
    private int lastOrderNumber;
    private volatile boolean printInProgress;
    private volatile boolean paidInProgress;
    private volatile boolean historyInProgress;
    private Button btnPrint;
    private Button btnFullReceipt;
    private Button btnPaid;
    private Button btnHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table);

        db = DatabaseHelper.getInstance(this);
        syncManager = SyncManager.getInstance(this);
        if (savedInstanceState != null) {
            tableId = savedInstanceState.getLong(STATE_TABLE_ID, -1);
        } else {
            tableId = getIntent().getLongExtra(EXTRA_TABLE_ID, -1);
        }

        tvTableInfo = (TextView) findViewById(R.id.tv_table_info);
        tvTotal = (TextView) findViewById(R.id.tv_total);
        spinnerStatus = (Spinner) findViewById(R.id.spinner_status);
        ListView lvOrderLines = (ListView) findViewById(R.id.lv_order_lines);

        spinnerStatus.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, TableStatus.displayNames()));

        orderAdapter = new OrderLineAdapter();
        lvOrderLines.setAdapter(orderAdapter);

        spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (spinnerInitializing || table == null) {
                    return;
                }
                table.setStatus(TableStatus.values()[position]);
                db.updateTable(table);
                pushOrderToMain();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        findViewById(R.id.btn_add_item).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMenuSectionPicker();
            }
        });

        btnPrint = (Button) findViewById(R.id.btn_print);
        btnFullReceipt = (Button) findViewById(R.id.btn_full_receipt);
        btnPaid = (Button) findViewById(R.id.btn_paid);
        btnHistory = (Button) findViewById(R.id.btn_history);

        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTableHistory();
            }
        });

        btnPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                printReceipt();
            }
        });

        btnFullReceipt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                printFullReceipt();
            }
        });

        btnPaid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markAsPaid();
            }
        });
    }

    private void setActionButtonsEnabled(boolean enabled) {
        if (btnPrint != null) {
            btnPrint.setEnabled(enabled);
        }
        if (btnFullReceipt != null) {
            btnFullReceipt.setEnabled(enabled);
        }
        if (btnPaid != null) {
            btnPaid.setEnabled(enabled);
        }
        if (btnHistory != null) {
            btnHistory.setEnabled(enabled && !historyInProgress);
        }
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_TABLE_ID, tableId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tableId > 0) {
            AppStateManager.setLastTableId(this, tableId);
        }
        reloadData();
    }

    @Override
    public void onSyncSuccess() {
        reloadData();
    }

    @Override
    public void onSyncFailed(String message) {
    }

    private void reloadData() {
        table = db.getTable(tableId);
        if (table == null) {
            finish();
            return;
        }

        tvTableInfo.setText(table.formatCardTitle() + " — "
                + (table.getDescription() == null ? "" : table.getDescription()));

        spinnerInitializing = true;
        spinnerStatus.setSelection(table.getStatus().ordinal(), false);
        spinnerInitializing = false;

        orderLines = db.getOrderLines(tableId);
        orderAdapter.notifyDataSetChanged();
        updateTotal();
        syncTableStatusWithCheck();
        if (table != null) {
            lastOrderNumber = table.getOpenOrderNumber();
        }
    }

    private void pushOrderToMain() {
        if (table != null) {
            OrderSync.pushAsync(this, table.getId(), table.getNumber(), table.getOpenOrderNumber(), orderLines);
        }
    }

    private void updateTotal() {
        double total = 0;
        for (OrderLine line : orderLines) {
            total += line.getLineTotal();
        }
        tvTotal.setText(getString(R.string.check_total,
                String.format(Locale.getDefault(), "%.2f", total)));
    }

    private void syncTableStatusWithCheck() {
        if (orderLines.isEmpty()) {
            return;
        }
        if (table.getStatus() == TableStatus.FREE) {
            table.setStatus(TableStatus.BUSY);
            db.updateTable(table);
            spinnerInitializing = true;
            spinnerStatus.setSelection(TableStatus.BUSY.ordinal(), false);
            spinnerInitializing = false;
        }
    }

    private void showTableHistory() {
        if (table == null || historyInProgress) {
            return;
        }

        historyInProgress = true;
        setActionButtonsEnabled(false);

        final int tableNumber = table.getNumber();
        final String tableLabel = table.getDisplayLabel();
        final List<OrderLine> linesCopy = new ArrayList<>(orderLines);
        final int openOrderNumber = table.getOpenOrderNumber();
        final int orderNumberFallback = lastOrderNumber;
        final int paidLimit = linesCopy.isEmpty()
                ? TableOrderHistoryBuilder.MAX_ENTRIES
                : TableOrderHistoryBuilder.MAX_ENTRIES - 1;

        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONArray paidOrders = null;
                if (ServerConfig.isConfigured(TableActivity.this)) {
                    try {
                        JSONObject response = CafeClient.getTableHistory(
                                TableActivity.this, tableNumber, paidLimit);
                        paidOrders = response.optJSONArray("orders");
                    } catch (Exception e) {
                        ErrorLogHelper.log(TableActivity.this, "История заказов", e);
                    }
                }

                try {
                    final List<TableOrderHistoryBuilder.Entry> entries = TableOrderHistoryBuilder.build(
                            linesCopy, openOrderNumber, orderNumberFallback, paidOrders);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            historyInProgress = false;
                            setActionButtonsEnabled(true);
                            if (isFinishing()) {
                                return;
                            }
                            if (entries.isEmpty()) {
                                Toast.makeText(TableActivity.this,
                                        R.string.table_history_empty, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            View view = LayoutInflater.from(TableActivity.this)
                                    .inflate(R.layout.dialog_table_history, null);
                            TextView tvContent = (TextView) view.findViewById(R.id.tv_history_content);
                            tvContent.setText(formatHistoryText(entries));
                            new AlertDialog.Builder(TableActivity.this)
                                    .setTitle(getString(R.string.table_history_title, tableLabel, tableNumber))
                                    .setView(view)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                    });
                } catch (final Exception e) {
                    ErrorLogHelper.log(TableActivity.this, "История заказов", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            historyInProgress = false;
                            setActionButtonsEnabled(true);
                            if (!isFinishing()) {
                                Toast.makeText(TableActivity.this,
                                        R.string.table_history_empty, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private String formatHistoryText(List<TableOrderHistoryBuilder.Entry> entries) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < entries.size(); i++) {
            TableOrderHistoryBuilder.Entry entry = entries.get(i);
            if (i > 0) {
                sb.append("\n\n");
            }

            String status = entry.current
                    ? getString(R.string.order_status_current)
                    : getString(R.string.order_status_paid);
            if (entry.current && entry.orderNumber <= 0) {
                sb.append(getString(R.string.order_history_current));
            } else {
                sb.append(getString(R.string.order_history_header, entry.orderNumber, status));
            }

            if (!entry.current && entry.paidAtMs > 0) {
                sb.append('\n').append(dateFormat.format(new Date(entry.paidAtMs)));
            }

            sb.append("\n────────────────\n");
            if (entry.itemsText != null && entry.itemsText.length() > 0) {
                sb.append(entry.itemsText).append('\n');
            }
            sb.append(getString(R.string.order_history_total,
                    String.format(Locale.getDefault(), "%.2f", entry.totalAmount),
                    entry.itemCount));
        }
        return sb.toString();
    }

    private void openMenuSectionPicker() {
        if (db.getAllMenuItems().isEmpty()) {
            Toast.makeText(this, R.string.menu_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, MenuSectionActivity.class);
        intent.putExtra(MenuSectionActivity.EXTRA_TABLE_ID, tableId);
        startActivity(intent);
    }

    private void printReceipt() {
        if (orderLines.isEmpty()) {
            Toast.makeText(this, R.string.check_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (printInProgress) {
            return;
        }
        printInProgress = true;
        setActionButtonsEnabled(false);

        final long syncTableId = table.getId();
        final int tableNumber = table.getNumber();
        final List<OrderLine> linesCopy = new ArrayList<>(orderLines);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OrderSync.pushSync(TableActivity.this, syncTableId, tableNumber,
                            table.getOpenOrderNumber(), linesCopy);
                    JSONObject result = CafeClient.print(TableActivity.this, syncTableId, tableNumber, linesCopy);
                    lastOrderNumber = result.getInt("orderNumber");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            printInProgress = false;
                            setActionButtonsEnabled(true);
                            if (!isFinishing()) {
                                Toast.makeText(TableActivity.this,
                                        getString(R.string.print_order_number, lastOrderNumber),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    ErrorLogHelper.log(TableActivity.this, "Печать", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            printInProgress = false;
                            setActionButtonsEnabled(true);
                            if (!isFinishing()) {
                                Toast.makeText(TableActivity.this,
                                        getString(R.string.print_failed, UserFacingErrors.format(TableActivity.this, e)),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void printFullReceipt() {
        if (orderLines.isEmpty()) {
            Toast.makeText(this, R.string.check_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (printInProgress) {
            return;
        }
        printInProgress = true;
        setActionButtonsEnabled(false);

        final long syncTableId = table.getId();
        final int tableNumber = table.getNumber();
        final List<OrderLine> linesCopy = new ArrayList<>(orderLines);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OrderSync.pushSync(TableActivity.this, syncTableId, tableNumber,
                            table.getOpenOrderNumber(), linesCopy);
                    JSONObject result = CafeClient.printFull(TableActivity.this, syncTableId, tableNumber, linesCopy);
                    lastOrderNumber = result.getInt("orderNumber");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            printInProgress = false;
                            setActionButtonsEnabled(true);
                            if (!isFinishing()) {
                                Toast.makeText(TableActivity.this,
                                        getString(R.string.full_receipt_sent_number, lastOrderNumber),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    ErrorLogHelper.log(TableActivity.this, "Полный чек", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            printInProgress = false;
                            setActionButtonsEnabled(true);
                            if (!isFinishing()) {
                                Toast.makeText(TableActivity.this,
                                        getString(R.string.print_failed, UserFacingErrors.format(TableActivity.this, e)),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void markAsPaid() {
        if (orderLines.isEmpty()) {
            Toast.makeText(this, R.string.check_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (paidInProgress || printInProgress) {
            return;
        }
        paidInProgress = true;
        setActionButtonsEnabled(false);

        double total = 0;
        int itemCount = 0;
        StringBuilder itemsText = new StringBuilder();
        for (OrderLine line : orderLines) {
            total += line.getLineTotal();
            itemCount += line.getCount();
            itemsText.append(line.getName()).append(" x").append(line.getCount())
                    .append(" = ").append(String.format(Locale.getDefault(), "%.2f", line.getLineTotal()))
                    .append("\n");
        }

        final long syncTableId = table.getId();
        final int tableNumber = table.getNumber();
        final int orderNumber = lastOrderNumber;
        final String items = itemsText.toString().trim();
        final double totalAmount = total;
        final int count = itemCount;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OrderSync.pushSync(TableActivity.this, syncTableId, tableNumber,
                            table.getOpenOrderNumber(), orderLines);
                    CafeClient.paid(TableActivity.this, syncTableId, tableNumber, orderNumber, items, totalAmount, count);
                    db.clearOrderLines(tableId);
                    table.setStatus(TableStatus.FREE);
                    db.updateTable(table);
                    AppStateManager.clearLastTableId(TableActivity.this);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            paidInProgress = false;
                            Toast.makeText(TableActivity.this, R.string.paid_sent_main, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                } catch (final Exception e) {
                    ErrorLogHelper.log(TableActivity.this, "Оплата", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            paidInProgress = false;
                            setActionButtonsEnabled(true);
                            Toast.makeText(TableActivity.this,
                                    getString(R.string.paid_failed,
                                            UserFacingErrors.format(TableActivity.this, e)),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private class OrderLineAdapter extends android.widget.BaseAdapter {
        @Override
        public int getCount() {
            return orderLines.size();
        }

        @Override
        public Object getItem(int position) {
            return orderLines.get(position);
        }

        @Override
        public long getItemId(int position) {
            return orderLines.get(position).getId();
        }

        @Override
        public View getView(final int position, View convertView, android.view.ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_order_line, parent, false);
            }

            final OrderLine line = orderLines.get(position);
            TextView tvLine = (TextView) view.findViewById(R.id.tv_order_line);
            TextView btnRemove = (TextView) view.findViewById(R.id.btn_remove_line);

            tvLine.setText(line.getName() + " — " + line.getCount() + " x "
                    + String.format(Locale.getDefault(), "%.2f", line.getPrice()) + " = "
                    + String.format(Locale.getDefault(), "%.2f", line.getLineTotal()));

            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (line.getCount() > 1) {
                        line.setCount(line.getCount() - 1);
                        if (line.getPrintedCount() > line.getCount()) {
                            line.setPrintedCount(line.getCount());
                        }
                        db.updateOrderLine(line);
                    } else {
                        db.deleteOrderLine(line.getId());
                    }
                    reloadData();
                    pushOrderToMain();
                }
            });
            return view;
        }
    }
}
