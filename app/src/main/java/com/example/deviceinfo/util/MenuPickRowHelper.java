package com.example.deviceinfo.util;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.deviceinfo.R;
import com.example.deviceinfo.model.MenuItem;

public final class MenuPickRowHelper {

    private MenuPickRowHelper() {
    }

    public static void bind(View row, MenuItem item, String detailsText, View.OnClickListener onRowClick) {
        bind(row, item, detailsText, 0, onRowClick, null);
    }

    public static void bind(View row, MenuItem item, String detailsText, int count,
                            View.OnClickListener onRowClick, View.OnClickListener onMinusClick) {
        TextView tvName = (TextView) row.findViewById(R.id.tv_pick_name);
        TextView tvDetails = (TextView) row.findViewById(R.id.tv_pick_details);
        TextView btnInfo = (TextView) row.findViewById(R.id.btn_item_info);
        TextView btnMinus = (TextView) row.findViewById(R.id.btn_pick_minus);

        tvName.setText(item.getName());
        tvDetails.setText(detailsText);
        row.setOnClickListener(onRowClick);
        if (btnMinus != null) {
            btnMinus.setOnClickListener(onMinusClick);
        }
        updateCount(row, count);

        final String description = item.getDescription();
        if (!TextUtils.isEmpty(description) && description.trim().length() > 0) {
            btnInfo.setVisibility(View.VISIBLE);
            btnInfo.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(v.getContext(), description.trim(), Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        } else {
            btnInfo.setVisibility(View.GONE);
            btnInfo.setOnLongClickListener(null);
        }
    }

    public static void updateCount(View row, int count) {
        TextView btnMinus = (TextView) row.findViewById(R.id.btn_pick_minus);
        TextView tvCount = (TextView) row.findViewById(R.id.tv_pick_count);
        if (btnMinus == null || tvCount == null) {
            return;
        }
        if (count > 0) {
            btnMinus.setVisibility(View.VISIBLE);
            tvCount.setVisibility(View.VISIBLE);
            tvCount.setText(String.valueOf(count));
        } else {
            btnMinus.setVisibility(View.GONE);
            tvCount.setVisibility(View.GONE);
            tvCount.setText("");
        }
    }
}
