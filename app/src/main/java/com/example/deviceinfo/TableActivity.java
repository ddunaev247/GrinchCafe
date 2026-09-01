package com.example.deviceinfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.deviceinfo.db.DatabaseHelper;
import com.example.deviceinfo.model.OrderHistory;
import com.example.deviceinfo.model.OrderLine;
import com.example.deviceinfo.model.RestaurantTable;
import com.example.deviceinfo.model.TableStatus;
import com.example.deviceinfo.util.AppStateManager;
import com.example.deviceinfo.util.ErrorLogHelper;
import com.example.deviceinfo.util.NetworkPrinterConfig;
import com.example.deviceinfo.util.NetworkPrinterSender;
import com.example.deviceinfo.util.OrderEvents;
import com.example.deviceinfo.util.PrintSessionHelper;
import com.example.deviceinfo.util.ReceiptPrinter;
import com.example.deviceinfo.util.TableOrderHistoryBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TableActivity extends AppCompatActivity {

    public static final String EXTRA_TABLE_ID = "table_id";
    private static final String STATE_TABLE_ID = "state_table_id";

    private DatabaseHelper db;
    private long tableId;
    private RestaurantTable table;
    private List<OrderLine> orderLines = new ArrayList<>();
    private OrderLineAdapter orderAdapter;
    private TextView tvTableInfo;
    private TextView tvTotal;
    private Spinner spinnerStatus;
    private PrintSessionHelper.PrintPlan pendingPrintPlan;
    private boolean pendingBarOnlyPrint;
    private boolean spinnerInitializing;
    private int lastOrderNumber;
    private boolean kitchenPrintSent;

    private final BroadcastReceiver orderChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadData();
        }
    };

    private final BroadcastReceiver usbPrintReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ReceiptPrinter.ACTION_USB_PRINT_PERMISSION.equals(intent.getAction())) {
                if (intent.getBooleanExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (pendingPrintPlan != null) {
                        PrintSessionHelper.PrintPlan plan = pendingPrintPlan;
                        pendingPrintPlan = null;
                        if (pendingBarOnlyPrint) {
                            sendFullReceiptToBar(plan);
                        } else {
                            sendToPosPrinter(plan);
                        }
                    }
                } else {
                    showToast(R.string.print_permission_denied);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table);

        db = DatabaseHelper.getInstance(this);
        if (savedInstanceState != null) {
            tableId = savedInstanceState.getLong(STATE_TABLE_ID, -1);
        } else {
            tableId = getIntent().getLongExtra(EXTRA_TABLE_ID, -1);
        }

        tvTableInfo = (TextView) findViewById(R.id.tv_table_info);
        tvTotal = (TextView) findViewById(R.id.tv_total);
        spinnerStatus = (Spinner) findViewById(R.id.spinner_status);
        ListView lvOrderLines = (ListView) findViewById(R.id.lv_order_lines);

        registerReceiver(usbPrintReceiver, new IntentFilter(ReceiptPrinter.ACTION_USB_PRINT_PERMISSION));

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

        findViewById(R.id.btn_history).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTableHistory();
            }
        });

        findViewById(R.id.btn_print).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                printReceipt();
            }
        });

        findViewById(R.id.btn_full_receipt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                printFullReceipt();
            }
        });

        findViewById(R.id.btn_paid).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markAsPaid();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_TABLE_ID, tableId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(usbPrintReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                orderChangedReceiver, new IntentFilter(OrderEvents.ACTION_ORDER_CHANGED));
        if (tableId > 0) {
            AppStateManager.setLastTableId(this, tableId);
        }
        reloadData();
    }

    @Override
    protected void onPause() {
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(orderChangedReceiver);
        } catch (Exception ignored) {
        }
        super.onPause();
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
        lastOrderNumber = table.getOpenOrderNumber();
    }

    private void updateTotal() {
        double total = 0;
        for (OrderLine line : orderLines) {
            total += line.getLineTotal();
        }
        tvTotal.setText(getString(R.string.check_total,
                String.format(Locale.getDefault(), "%.2f", total)));
    }

    private void changeLineCount(OrderLine line, int delta) {
        int newCount = line.getCount() + delta;
        if (newCount <= 0) {
            db.deleteOrderLine(line.getId());
        } else {
            line.setCount(newCount);
            if (line.getPrintedCount() > newCount) {
                line.setPrintedCount(newCount);
            }
            db.updateOrderLine(line);
        }
        OrderEvents.notifyChanged(this);
        reloadData();
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
        if (table == null) {
            return;
        }

        List<TableOrderHistoryBuilder.Entry> entries = TableOrderHistoryBuilder.build(
                db, tableId, table.getNumber(), orderLines,
                table.getOpenOrderNumber(), lastOrderNumber);

        if (entries.isEmpty()) {
            showToast(R.string.table_history_empty);
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_table_history, null);
        TextView tvContent = (TextView) view.findViewById(R.id.tv_history_content);
        tvContent.setText(formatHistoryText(entries));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.table_history_title, table.getDisplayLabel(), table.getNumber()))
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .show();
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
            showToast(R.string.menu_empty);
            return;
        }
        Intent intent = new Intent(this, MenuSectionActivity.class);
        intent.putExtra(MenuSectionActivity.EXTRA_TABLE_ID, tableId);
        startActivity(intent);
    }

    private void printReceipt() {
        if (orderLines.isEmpty()) {
            showToast(R.string.check_empty);
            return;
        }

        final PrintSessionHelper.PrintPlan plan =
                PrintSessionHelper.buildPlan(this, db, tableId, table.getNumber());
        if (plan == null || plan.isEmpty()) {
            showToast(R.string.print_nothing_new);
            return;
        }

        lastOrderNumber = plan.orderNumber;
        kitchenPrintSent = false;
        pendingBarOnlyPrint = false;
        sendToPosPrinter(plan);
    }

    private void printFullReceipt() {
        if (orderLines.isEmpty()) {
            showToast(R.string.check_empty);
            return;
        }

        final PrintSessionHelper.PrintPlan plan =
                PrintSessionHelper.buildFullBarPlan(this, db, tableId, table.getNumber());
        if (plan == null || plan.isEmpty()) {
            showToast(R.string.check_empty);
            return;
        }

        lastOrderNumber = plan.orderNumber;
        pendingBarOnlyPrint = true;
        sendFullReceiptToBar(plan);
    }

    /** Только USB-бар, без «+++» и без кухни. Не помечает позиции напечатанными. */
    private void sendFullReceiptToBar(final PrintSessionHelper.PrintPlan plan) {
        pendingPrintPlan = plan;
        pendingBarOnlyPrint = true;
        ReceiptPrinter.printBarReceipt(this, plan.barReceipt, new ReceiptPrinter.PrintCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing()) {
                    return;
                }
                pendingPrintPlan = null;
                pendingBarOnlyPrint = false;
                showToast(R.string.full_receipt_sent);
            }

            @Override
            public void onFailure(String message) {
                if (isFinishing()) {
                    return;
                }
                ErrorLogHelper.log(TableActivity.this, "Полный чек (бар)", message);
                showToast(message);
            }

            @Override
            public void onPermissionRequired(UsbDevice device) {
                if (isFinishing()) {
                    return;
                }
                pendingPrintPlan = plan;
                pendingBarOnlyPrint = true;
                ReceiptPrinter.requestUsbPermission(TableActivity.this, device);
                showToast(R.string.print_permission_request);
            }
        });
    }

    /** USB (бар — кухонный формат), затем сеть (кухня). */
    private void sendToPosPrinter(final PrintSessionHelper.PrintPlan plan) {
        pendingPrintPlan = plan;
        pendingBarOnlyPrint = false;

        if (!plan.hasBarKitchenPrint() && !plan.hasGuestBarPrint()) {
            sendToNetworkPrinter(true, plan, plan);
            return;
        }

        ReceiptPrinter.PrintCallback callback = new ReceiptPrinter.PrintCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing()) {
                    return;
                }
                onPrintDelivered(plan);
                showToast(R.string.print_sent_pos);
                sendToNetworkPrinter(false, null, plan);
            }

            @Override
            public void onFailure(String message) {
                if (isFinishing()) {
                    return;
                }
                ErrorLogHelper.log(TableActivity.this, "USB печать", message);
                showToast(message);
                sendToNetworkPrinter(true, plan, plan);
            }

            @Override
            public void onPermissionRequired(UsbDevice device) {
                if (isFinishing()) {
                    return;
                }
                pendingPrintPlan = plan;
                ReceiptPrinter.requestUsbPermission(TableActivity.this, device);
                showToast(R.string.print_permission_request);
                sendToNetworkPrinter(true, plan, plan);
            }
        };

        if (plan.hasBarKitchenPrint()) {
            ReceiptPrinter.printKitchenStyleReceipt(this, plan.barKitchenReceipt, callback);
        } else {
            ReceiptPrinter.printBarReceipt(this, plan.barReceipt, callback);
        }
    }

    private void sendToNetworkPrinter(final boolean markOnSuccess,
                                      final PrintSessionHelper.PrintPlan markPlan,
                                      final PrintSessionHelper.PrintPlan printPlan) {
        if (!NetworkPrinterConfig.isConfigured(this)) {
            if (markOnSuccess) {
                onPrintDelivered(markPlan);
            }
            return;
        }
        if (printPlan == null || !printPlan.hasKitchenPrint()) {
            if (markOnSuccess) {
                onPrintDelivered(markPlan);
            }
            return;
        }
        if (kitchenPrintSent) {
            if (markOnSuccess) {
                onPrintDelivered(markPlan);
            }
            return;
        }
        kitchenPrintSent = true;
        NetworkPrinterSender.sendKitchenIfConfigured(this, printPlan.kitchenReceipt,
                new NetworkPrinterSender.SendCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing()) {
                    return;
                }
                if (markOnSuccess) {
                    onPrintDelivered(markPlan);
                }
                showToast(R.string.print_sent_network);
            }

            @Override
            public void onFailure(String message) {
                if (isFinishing()) {
                    return;
                }
                ErrorLogHelper.log(TableActivity.this, "Сетевая печать", message);
                showToast(getString(R.string.print_network_failed, message));
            }
        });
    }

    private void onPrintDelivered(PrintSessionHelper.PrintPlan plan) {
        if (plan == null) {
            return;
        }
        PrintSessionHelper.markPrintDelivered(db, plan);
        lastOrderNumber = plan.orderNumber;
        pendingPrintPlan = null;
        pendingBarOnlyPrint = false;
    }

    private void markAsPaid() {
        if (orderLines.isEmpty()) {
            showToast(R.string.check_empty);
            return;
        }

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

        int orderNumber = PrintSessionHelper.resolveOrderNumberForPayment(db, tableId, lastOrderNumber);

        OrderHistory history = new OrderHistory();
        history.setPaidAt(System.currentTimeMillis());
        history.setTableNumber(table.getNumber());
        history.setOrderNumber(orderNumber);
        history.setItemsText(itemsText.toString().trim());
        history.setTotalAmount(total);
        history.setItemCount(itemCount);
        db.insertOrderHistory(history);

        db.clearOrderLines(tableId);
        PrintSessionHelper.clearSession(db, tableId);
        table.setStatus(TableStatus.FREE);
        db.updateTable(table);

        AppStateManager.clearLastTableId(this);
        showToast(R.string.payment_saved);
        finish();
    }

    private void showToast(int resId) {
        if (!isFinishing()) {
            Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
        }
    }

    private void showToast(String message) {
        if (!isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
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
            TextView tvName = (TextView) view.findViewById(R.id.tv_order_name);
            TextView tvMeta = (TextView) view.findViewById(R.id.tv_order_meta);
            TextView tvCount = (TextView) view.findViewById(R.id.tv_order_count);
            TextView btnMinus = (TextView) view.findViewById(R.id.btn_line_minus);
            TextView btnPlus = (TextView) view.findViewById(R.id.btn_line_plus);

            tvName.setText(line.getName());
            tvCount.setText(String.valueOf(line.getCount()));

            String price = String.format(Locale.getDefault(), "%.2f", line.getPrice());
            String lineTotal = String.format(Locale.getDefault(), "%.2f", line.getLineTotal());
            tvMeta.setText(price + " × " + line.getCount() + " = " + lineTotal);

            btnMinus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    changeLineCount(line, -1);
                }
            });
            btnPlus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    changeLineCount(line, 1);
                }
            });
            return view;
        }
    }
}
