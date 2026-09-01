package com.grinchcafe.admin.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.grinchcafe.admin.MainActivity;
import com.grinchcafe.admin.R;
import com.grinchcafe.admin.db.DatabaseHelper;
import com.grinchcafe.admin.model.RestaurantTable;
import com.grinchcafe.admin.model.TableStatus;
import com.grinchcafe.admin.sync.SyncManager;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements SyncManager.Listener {

    private DatabaseHelper db;
    private TableAdapter adapter;
    private List<RestaurantTable> tables = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = DatabaseHelper.getInstance(getActivity());
        RecyclerView rvTables = (RecyclerView) view.findViewById(R.id.rv_tables);
        rvTables.setLayoutManager(new GridLayoutManager(getActivity(), 2));
        adapter = new TableAdapter();
        rvTables.setAdapter(adapter);
        loadTables();
    }

    @Override
    public void onStart() {
        super.onStart();
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.getSyncManager().addListener(this);
        }
    }

    @Override
    public void onStop() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.getSyncManager().removeListener(this);
        }
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTables();
    }

    private void loadTables() {
        tables = db.getAllTables();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onSyncSuccess() {
        loadTables();
    }

    @Override
    public void onSyncFailed(String message) {
    }

    private int statusColor(TableStatus status) {
        switch (status) {
            case BUSY:
                return ContextCompat.getColor(getActivity(), R.color.statusBusy);
            case RESERVED:
                return ContextCompat.getColor(getActivity(), R.color.statusReserved);
            case FREE:
            default:
                return ContextCompat.getColor(getActivity(), R.color.statusFree);
        }
    }

    private class TableAdapter extends RecyclerView.Adapter<TableAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_table_waiter, parent, false);
            return new ViewHolder(itemView);
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
            private final TextView tvNumber;
            private final TextView tvDescription;
            private final TextView tvStatus;

            ViewHolder(View itemView) {
                super(itemView);
                statusIndicator = itemView.findViewById(R.id.view_status_indicator);
                tvNumber = (TextView) itemView.findViewById(R.id.tv_table_number);
                tvDescription = (TextView) itemView.findViewById(R.id.tv_table_description);
                tvStatus = (TextView) itemView.findViewById(R.id.tv_table_status);
                itemView.setClickable(false);
                itemView.setFocusable(false);
            }

            void bind(RestaurantTable table) {
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
            }
        }
    }
}
