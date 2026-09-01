package com.grinchcafe.admin.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.grinchcafe.admin.MainActivity;
import com.grinchcafe.admin.R;
import com.grinchcafe.admin.net.ServerConfig;
import com.grinchcafe.admin.sync.SyncManager;
import com.grinchcafe.admin.util.ErrorLogHelper;
import com.grinchcafe.admin.util.UserFacingErrors;

public class SettingsFragment extends Fragment implements SyncManager.Listener {

    private LinearLayout panelSetup;
    private LinearLayout panelConnected;
    private EditText etServerHost;
    private TextView tvServerAddress;
    private boolean expectManualSyncResult;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        panelSetup = (LinearLayout) view.findViewById(R.id.panel_setup);
        panelConnected = (LinearLayout) view.findViewById(R.id.panel_connected);
        etServerHost = (EditText) view.findViewById(R.id.et_server_host);
        tvServerAddress = (TextView) view.findViewById(R.id.tv_server_address);

        view.findViewById(R.id.btn_save_server).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveInitialConfig();
            }
        });

        view.findViewById(R.id.btn_sync).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!ServerConfig.isConfigured(getActivity())) {
                    Toast.makeText(getActivity(), R.string.server_not_configured, Toast.LENGTH_SHORT).show();
                    return;
                }
                expectManualSyncResult = true;
                ((MainActivity) getActivity()).getSyncManager().syncManual();
            }
        });

        view.findViewById(R.id.btn_config).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfigDialog();
            }
        });

        updateConnectionUi();
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
        updateConnectionUi();
    }

    private void updateConnectionUi() {
        if (getActivity() == null) {
            return;
        }
        if (ServerConfig.isConfigured(getActivity())) {
            panelSetup.setVisibility(View.GONE);
            panelConnected.setVisibility(View.VISIBLE);
            tvServerAddress.setText(getString(R.string.connected_to,
                    ServerConfig.getDisplayAddress(getActivity())));
        } else {
            panelSetup.setVisibility(View.VISIBLE);
            panelConnected.setVisibility(View.GONE);
            etServerHost.setText(ServerConfig.getHost(getActivity()));
        }
    }

    private void saveInitialConfig() {
        String host = etServerHost.getText().toString().trim();
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(getActivity(), R.string.network_ip_required, Toast.LENGTH_SHORT).show();
            return;
        }
        ServerConfig.markConfigured(getActivity(), host, ServerConfig.DEFAULT_PORT);
        updateConnectionUi();
        MainActivity activity = (MainActivity) getActivity();
        activity.getSyncManager().start();
        expectManualSyncResult = true;
        activity.getSyncManager().syncManual();
        Toast.makeText(getActivity(), R.string.server_saved, Toast.LENGTH_SHORT).show();
    }

    private void showConfigDialog() {
        View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_server_config, null);
        final EditText etHost = (EditText) dialogView.findViewById(R.id.et_config_host);
        etHost.setText(ServerConfig.getHost(getActivity()));

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.configuration)
                .setView(dialogView)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String host = etHost.getText().toString().trim();
                        if (TextUtils.isEmpty(host)) {
                            Toast.makeText(getActivity(), R.string.network_ip_required,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ServerConfig.markConfigured(getActivity(), host, ServerConfig.DEFAULT_PORT);
                        updateConnectionUi();
                        expectManualSyncResult = true;
                        ((MainActivity) getActivity()).getSyncManager().syncManual();
                        Toast.makeText(getActivity(), R.string.server_saved, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onSyncSuccess() {
        if (expectManualSyncResult && getActivity() != null) {
            expectManualSyncResult = false;
            Toast.makeText(getActivity(), getString(R.string.sync_success_menu,
                    com.grinchcafe.admin.db.DatabaseHelper.getInstance(getActivity()).getAllMenuItems().size()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSyncFailed(String message) {
        expectManualSyncResult = false;
        if (getActivity() != null) {
            ErrorLogHelper.log(getActivity(), "Синхронизация", message);
            Toast.makeText(getActivity(), getString(R.string.sync_failed,
                    UserFacingErrors.format(getActivity(), message)), Toast.LENGTH_SHORT).show();
        }
    }
}
