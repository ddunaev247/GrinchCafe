package com.example.deviceinfo.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.deviceinfo.db.DatabaseHelper;
import com.example.deviceinfo.model.OrderHistory;
import com.example.deviceinfo.model.ReportPeriod;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class MonthlyReportScheduler {

    private static final String PREFS = "monthly_report_prefs";
    private static final String KEY_LAST_SAVED = "last_saved_month";

    private MonthlyReportScheduler() {
    }

    public static void checkAndSaveMonthlyReport(Context context) {
        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.DAY_OF_MONTH) != 1) {
            return;
        }

        String currentMonthKey = String.format(Locale.getDefault(), "%04d-%02d",
                now.get(Calendar.YEAR), now.get(Calendar.MONTH));

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (currentMonthKey.equals(prefs.getString(KEY_LAST_SAVED, ""))) {
            return;
        }

        Calendar from = Calendar.getInstance();
        from.add(Calendar.MONTH, -1);
        from.set(Calendar.DAY_OF_MONTH, 1);
        from.set(Calendar.HOUR_OF_DAY, 0);
        from.set(Calendar.MINUTE, 0);
        from.set(Calendar.SECOND, 0);
        from.set(Calendar.MILLISECOND, 0);

        Calendar to = (Calendar) from.clone();
        to.add(Calendar.MONTH, 1);

        DatabaseHelper db = DatabaseHelper.getInstance(context);
        List<OrderHistory> history = db.getOrderHistory(from.getTimeInMillis(), to.getTimeInMillis());
        ReportRange range = ReportRange.forAnchor(ReportPeriod.MONTH, from, db.getFirstOrderPaidAt());
        String report = ReportBuilder.buildReport(range, history);

        try {
            File file = ReportFileHelper.saveToDownloads(
                    context, ReportFileHelper.suggestedName(range), report);
            if (file != null) {
                prefs.edit().putString(KEY_LAST_SAVED, currentMonthKey).apply();
            }
        } catch (Exception ignored) {
        }
    }
}
