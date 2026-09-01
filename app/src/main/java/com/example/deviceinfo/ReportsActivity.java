package com.example.deviceinfo;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.deviceinfo.db.DatabaseHelper;
import com.example.deviceinfo.model.OrderHistory;
import com.example.deviceinfo.model.ReportPeriod;
import com.example.deviceinfo.util.ReportBuilder;
import com.example.deviceinfo.util.ReportFileHelper;
import com.example.deviceinfo.util.ErrorLogHelper;
import com.example.deviceinfo.util.ReportRange;
import com.example.deviceinfo.util.ReportStats;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.List;

public class ReportsActivity extends AppCompatActivity {

    private static final int REQUEST_SAVE_PERMISSION = 41;

    private DatabaseHelper db;
    private RadioGroup rgPeriod;
    private LinearLayout llDateNav;
    private CalendarView calendarReport;
    private TextView tvPeriodLabel;
    private TextView tvMonthActivity;
    private TextView tvStatOrders;
    private TextView tvStatItems;
    private TextView tvStatTotal;
    private TextView tvStatAverage;
    private TextView tvStatRange;
    private TextView tvDetails;

    private final Calendar anchorDate = Calendar.getInstance();
    private ReportPeriod currentPeriod = ReportPeriod.DAY;
    private String currentReportText = "";
    private ReportRange currentRange;
    private boolean calendarListenerEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        db = DatabaseHelper.getInstance(this);
        rgPeriod = (RadioGroup) findViewById(R.id.rg_period);
        llDateNav = (LinearLayout) findViewById(R.id.ll_date_nav);
        calendarReport = (CalendarView) findViewById(R.id.calendar_report);
        tvPeriodLabel = (TextView) findViewById(R.id.tv_period_label);
        tvMonthActivity = (TextView) findViewById(R.id.tv_month_activity);
        tvStatOrders = (TextView) findViewById(R.id.tv_stat_orders);
        tvStatItems = (TextView) findViewById(R.id.tv_stat_items);
        tvStatTotal = (TextView) findViewById(R.id.tv_stat_total);
        tvStatAverage = (TextView) findViewById(R.id.tv_stat_average);
        tvStatRange = (TextView) findViewById(R.id.tv_stat_range);
        tvDetails = (TextView) findViewById(R.id.tv_report_details);

        setupCalendarBounds();
        updatePeriodUi();

        rgPeriod.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                currentPeriod = resolvePeriod(checkedId);
                updatePeriodUi();
                refreshReport();
            }
        });

        calendarReport.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(CalendarView view, int year, int month, int dayOfMonth) {
                if (!calendarListenerEnabled) {
                    return;
                }
                anchorDate.set(Calendar.YEAR, year);
                anchorDate.set(Calendar.MONTH, month);
                anchorDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                clampAnchorToToday();
                refreshReport();
            }
        });

        findViewById(R.id.btn_period_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ReportRange.shiftAnchor(anchorDate, currentPeriod, -1);
                clampAnchorToToday();
                syncCalendarToAnchor();
                refreshReport();
            }
        });

        findViewById(R.id.btn_period_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ReportRange.shiftAnchor(anchorDate, currentPeriod, 1);
                clampAnchorToToday();
                syncCalendarToAnchor();
                refreshReport();
            }
        });

        findViewById(R.id.btn_refresh_report).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshReport();
            }
        });

        findViewById(R.id.btn_save_report).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveReport();
            }
        });

        findViewById(R.id.btn_clear_stats).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearStats();
            }
        });

        refreshReport();
    }

    private void setupCalendarBounds() {
        long firstOrder = db.getFirstOrderPaidAt();
        Calendar today = ReportRange.truncateToDay(Calendar.getInstance());
        if (firstOrder > 0) {
            calendarReport.setMinDate(firstOrder);
        } else {
            Calendar start = ReportRange.truncateToDay(Calendar.getInstance());
            start.add(Calendar.YEAR, -1);
            calendarReport.setMinDate(start.getTimeInMillis());
        }
        calendarReport.setMaxDate(today.getTimeInMillis());
    }

    private ReportPeriod resolvePeriod(int checkedId) {
        if (checkedId == R.id.rb_month) {
            return ReportPeriod.MONTH;
        }
        if (checkedId == R.id.rb_year) {
            return ReportPeriod.YEAR;
        }
        if (checkedId == R.id.rb_all) {
            return ReportPeriod.ALL;
        }
        return ReportPeriod.DAY;
    }

    private void updatePeriodUi() {
        boolean showDateControls = currentPeriod != ReportPeriod.ALL;
        llDateNav.setVisibility(showDateControls ? View.VISIBLE : View.GONE);
        calendarReport.setVisibility(
                currentPeriod == ReportPeriod.DAY || currentPeriod == ReportPeriod.MONTH
                        ? View.VISIBLE : View.GONE);
        tvMonthActivity.setVisibility(currentPeriod == ReportPeriod.MONTH ? View.VISIBLE : View.GONE);
        syncCalendarToAnchor();
    }

    private void syncCalendarToAnchor() {
        calendarListenerEnabled = false;
        calendarReport.setDate(anchorDate.getTimeInMillis(), false, true);
        calendarListenerEnabled = true;
    }

    private void refreshReport() {
        long firstOrder = db.getFirstOrderPaidAt();
        currentRange = ReportRange.forAnchor(currentPeriod, anchorDate, firstOrder);

        List<OrderHistory> history = db.getOrderHistory(
                currentRange.fromInclusive, currentRange.toExclusive);
        DatabaseHelper.HistoryAggregate aggregate = db.getHistoryAggregate(
                currentRange.fromInclusive, currentRange.toExclusive);

        currentReportText = ReportBuilder.buildReport(currentRange, history);
        tvPeriodLabel.setText(currentRange.navLabel);
        tvStatRange.setText(currentRange.formatPeriodLine());

        tvStatOrders.setText(getString(R.string.report_stat_orders, aggregate.orderCount));
        tvStatItems.setText(getString(R.string.report_stat_items, aggregate.itemCount));
        BigDecimal total = ReportStats.money(aggregate.totalAmount);
        tvStatTotal.setText(getString(R.string.report_stat_total,
                ReportStats.formatMoney(total)));

        if (aggregate.orderCount > 0) {
            tvStatAverage.setVisibility(View.VISIBLE);
            BigDecimal average = total.divide(
                    BigDecimal.valueOf(aggregate.orderCount), 2, RoundingMode.HALF_UP);
            tvStatAverage.setText(getString(R.string.report_stat_average,
                    ReportStats.formatMoney(average)));
        } else {
            tvStatAverage.setVisibility(View.GONE);
        }

        tvDetails.setText(currentReportText);
        updateMonthActivity(history);
    }

    private void clampAnchorToToday() {
        Calendar today = ReportRange.truncateToDay(Calendar.getInstance());
        if (anchorDate.after(today)) {
            anchorDate.setTimeInMillis(today.getTimeInMillis());
        }
    }

    private void updateMonthActivity(List<OrderHistory> history) {
        if (currentPeriod != ReportPeriod.MONTH) {
            tvMonthActivity.setVisibility(View.GONE);
            return;
        }
        ReportStats stats = ReportStats.fromHistory(history);
        if (stats.byDay.isEmpty()) {
            tvMonthActivity.setText(R.string.report_month_no_activity);
            tvMonthActivity.setVisibility(View.VISIBLE);
            return;
        }
        StringBuilder sb = new StringBuilder(getString(R.string.report_month_activity_title));
        sb.append("\n");
        for (ReportStats.DayStats day : stats.sortedDays()) {
            sb.append(ReportBuilder.formatDayDisplay(day.dayKey))
                    .append(": ")
                    .append(getString(R.string.report_day_line, day.orderCount, day.itemCount,
                            ReportStats.formatMoney(day.totalAmount)))
                    .append("\n");
        }
        tvMonthActivity.setText(sb.toString().trim());
        tvMonthActivity.setVisibility(View.VISIBLE);
    }

    private void saveReport() {
        if (TextUtils.isEmpty(currentReportText)) {
            Toast.makeText(this, R.string.report_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_SAVE_PERMISSION);
            return;
        }
        writeReportFile();
    }

    private void confirmClearStats() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_stats)
                .setMessage(R.string.clear_stats_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.clear_stats, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        db.clearOrderHistory();
                        setupCalendarBounds();
                        refreshReport();
                        Toast.makeText(ReportsActivity.this,
                                R.string.clear_stats_done, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void writeReportFile() {
        try {
            File file = ReportFileHelper.saveToDownloads(
                    this, ReportFileHelper.suggestedName(currentRange), currentReportText);
            Toast.makeText(this,
                    getString(R.string.report_saved, file.getName()),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            ErrorLogHelper.log(this, "Сохранение отчёта", e);
            Toast.makeText(this, R.string.report_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_SAVE_PERMISSION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            writeReportFile();
        } else {
            Toast.makeText(this, R.string.report_save_permission, Toast.LENGTH_LONG).show();
        }
    }
}
