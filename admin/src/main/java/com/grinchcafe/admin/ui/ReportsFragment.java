package com.grinchcafe.admin.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.grinchcafe.admin.R;
import com.grinchcafe.admin.model.ReportPeriod;
import com.grinchcafe.admin.net.AdminClient;
import com.grinchcafe.admin.net.ServerConfig;
import com.grinchcafe.admin.util.ErrorLogHelper;
import com.grinchcafe.admin.util.ReportFileHelper;
import com.grinchcafe.admin.util.ReportRange;
import com.grinchcafe.admin.util.UserFacingErrors;

import org.json.JSONObject;

import java.io.File;
import java.util.Calendar;

public class ReportsFragment extends Fragment {

    private static final int REQUEST_SAVE_PERMISSION = 41;

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
    private long firstOrderAt;
    private boolean calendarListenerEnabled = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reports, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rgPeriod = (RadioGroup) view.findViewById(R.id.rg_period);
        llDateNav = (LinearLayout) view.findViewById(R.id.ll_date_nav);
        calendarReport = (CalendarView) view.findViewById(R.id.calendar_report);
        tvPeriodLabel = (TextView) view.findViewById(R.id.tv_period_label);
        tvMonthActivity = (TextView) view.findViewById(R.id.tv_month_activity);
        tvStatOrders = (TextView) view.findViewById(R.id.tv_stat_orders);
        tvStatItems = (TextView) view.findViewById(R.id.tv_stat_items);
        tvStatTotal = (TextView) view.findViewById(R.id.tv_stat_total);
        tvStatAverage = (TextView) view.findViewById(R.id.tv_stat_average);
        tvStatRange = (TextView) view.findViewById(R.id.tv_stat_range);
        tvDetails = (TextView) view.findViewById(R.id.tv_report_details);

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
            public void onSelectedDayChange(CalendarView cal, int year, int month, int dayOfMonth) {
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

        view.findViewById(R.id.btn_period_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ReportRange.shiftAnchor(anchorDate, currentPeriod, -1);
                clampAnchorToToday();
                syncCalendarToAnchor();
                refreshReport();
            }
        });

        view.findViewById(R.id.btn_period_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ReportRange.shiftAnchor(anchorDate, currentPeriod, 1);
                clampAnchorToToday();
                syncCalendarToAnchor();
                refreshReport();
            }
        });

        view.findViewById(R.id.btn_refresh_report).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshReport();
            }
        });

        view.findViewById(R.id.btn_save_report).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveReport();
            }
        });

        refreshReport();
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

    private void clampAnchorToToday() {
        Calendar today = ReportRange.truncateToDay(Calendar.getInstance());
        if (anchorDate.after(today)) {
            anchorDate.setTimeInMillis(today.getTimeInMillis());
        }
    }

    private void setupCalendarBounds() {
        Calendar today = ReportRange.truncateToDay(Calendar.getInstance());
        if (firstOrderAt > 0) {
            calendarReport.setMinDate(firstOrderAt);
        }
        calendarReport.setMaxDate(today.getTimeInMillis());
    }

    private void refreshReport() {
        if (getActivity() == null) {
            return;
        }
        if (!ServerConfig.isConfigured(getActivity())) {
            Toast.makeText(getActivity(), R.string.server_not_configured, Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject data = AdminClient.fetchReports(
                            getActivity(), currentPeriod.name(), anchorDate.getTimeInMillis());
                    if (getActivity() == null) {
                        return;
                    }
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            applyReportData(data);
                        }
                    });
                } catch (final Exception e) {
                    if (getActivity() == null) {
                        return;
                    }
                    ErrorLogHelper.log(getActivity(), "Загрузка отчёта", e);
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getActivity(),
                                    getString(R.string.report_load_failed,
                                            UserFacingErrors.format(getActivity(), e)),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void applyReportData(JSONObject data) {
        firstOrderAt = data.optLong("firstOrderAt", 0);
        setupCalendarBounds();

        currentRange = ReportRange.forAnchor(currentPeriod, anchorDate, firstOrderAt);
        currentReportText = data.optString("detailsText", "");

        tvPeriodLabel.setText(data.optString("periodLabel", currentRange.navLabel));
        tvStatRange.setText(data.optString("periodLine", currentRange.formatPeriodLine()));
        tvStatOrders.setText(getString(R.string.report_stat_orders, data.optInt("orderCount", 0)));
        tvStatItems.setText(getString(R.string.report_stat_items, data.optInt("itemCount", 0)));
        tvStatTotal.setText(getString(R.string.report_stat_total, data.optString("total", "0.00")));

        String average = data.optString("average", "");
        if (!TextUtils.isEmpty(average)) {
            tvStatAverage.setVisibility(View.VISIBLE);
            tvStatAverage.setText(getString(R.string.report_stat_average, average));
        } else {
            tvStatAverage.setVisibility(View.GONE);
        }

        tvDetails.setText(currentReportText);

        if (currentPeriod == ReportPeriod.MONTH) {
            String monthActivity = data.optString("monthActivity", "");
            if (TextUtils.isEmpty(monthActivity)) {
                tvMonthActivity.setText(R.string.report_month_no_activity);
            } else {
                tvMonthActivity.setText(getString(R.string.report_month_activity_title)
                        + "\n" + monthActivity);
            }
            tvMonthActivity.setVisibility(View.VISIBLE);
        } else {
            tvMonthActivity.setVisibility(View.GONE);
        }
    }

    private void saveReport() {
        if (TextUtils.isEmpty(currentReportText)) {
            Toast.makeText(getActivity(), R.string.report_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_SAVE_PERMISSION);
            return;
        }
        writeReportFile();
    }

    private void writeReportFile() {
        try {
            File file = ReportFileHelper.saveToDownloads(
                    getActivity(), ReportFileHelper.suggestedName(currentRange), currentReportText);
            Toast.makeText(getActivity(),
                    getString(R.string.report_saved, file.getName()),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            ErrorLogHelper.log(getActivity(), "Сохранение отчёта", e);
            Toast.makeText(getActivity(), R.string.report_save_failed, Toast.LENGTH_LONG).show();
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
            Toast.makeText(getActivity(), R.string.report_save_permission, Toast.LENGTH_LONG).show();
        }
    }
}
