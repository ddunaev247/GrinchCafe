package com.grinchcafe.admin;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;

import com.grinchcafe.admin.net.ServerConfig;
import com.grinchcafe.admin.sync.SyncManager;
import com.grinchcafe.admin.ui.HomeFragment;
import com.grinchcafe.admin.ui.MenuFragment;
import com.grinchcafe.admin.ui.ReportsFragment;
import com.grinchcafe.admin.ui.SettingsFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private SyncManager syncManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        syncManager = SyncManager.getInstance(this);
        if (ServerConfig.isConfigured(this)) {
            syncManager.start();
        }

        bottomNav = (BottomNavigationView) findViewById(R.id.bottom_nav);
        bottomNav.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull android.view.MenuItem item) {
                Fragment fragment;
                int id = item.getItemId();
                if (id == R.id.nav_menu) {
                    fragment = new MenuFragment();
                } else if (id == R.id.nav_reports) {
                    fragment = new ReportsFragment();
                } else if (id == R.id.nav_settings) {
                    fragment = new SettingsFragment();
                } else {
                    fragment = new HomeFragment();
                }
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit();
                return true;
            }
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }
}
