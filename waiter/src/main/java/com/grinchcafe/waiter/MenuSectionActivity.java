package com.grinchcafe.waiter;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.grinchcafe.waiter.model.MenuSection;

public class MenuSectionActivity extends AppCompatActivity {

    public static final String EXTRA_TABLE_ID = "table_id";

    private long tableId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_section);

        tableId = getIntent().getLongExtra(EXTRA_TABLE_ID, -1);
        if (tableId <= 0) {
            finish();
            return;
        }

        findViewById(R.id.btn_section_lunch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MenuSectionActivity.this, LunchTypeActivity.class);
                intent.putExtra(LunchTypeActivity.EXTRA_TABLE_ID, tableId);
                startActivity(intent);
            }
        });

        findViewById(R.id.btn_section_main).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPick(MenuSection.MAIN, null);
            }
        });

        findViewById(R.id.btn_section_bar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPick(MenuSection.BAR, null);
            }
        });
    }

    private void openPick(MenuSection section, Boolean complexOnly) {
        Intent intent = new Intent(this, MenuPickActivity.class);
        intent.putExtra(MenuPickActivity.EXTRA_TABLE_ID, tableId);
        intent.putExtra(MenuPickActivity.EXTRA_SECTION, section.name());
        if (complexOnly != null) {
            intent.putExtra(MenuPickActivity.EXTRA_COMPLEX_ONLY, complexOnly);
            intent.putExtra(MenuPickActivity.EXTRA_HAS_COMPLEX_FILTER, true);
        }
        startActivity(intent);
    }
}
