package com.grinchcafe.waiter;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.grinchcafe.waiter.model.MenuSection;

public class LunchTypeActivity extends AppCompatActivity {

    public static final String EXTRA_TABLE_ID = "table_id";

    private long tableId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lunch_type);

        tableId = getIntent().getLongExtra(EXTRA_TABLE_ID, -1);
        if (tableId <= 0) {
            finish();
            return;
        }

        findViewById(R.id.btn_lunch_full_list).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPick();
            }
        });

        findViewById(R.id.btn_lunch_complex).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LunchTypeActivity.this, ComplexPickActivity.class);
                intent.putExtra(ComplexPickActivity.EXTRA_TABLE_ID, tableId);
                startActivity(intent);
            }
        });
    }

    private void openPick() {
        Intent intent = new Intent(this, MenuPickActivity.class);
        intent.putExtra(MenuPickActivity.EXTRA_TABLE_ID, tableId);
        intent.putExtra(MenuPickActivity.EXTRA_SECTION, MenuSection.LUNCH.name());
        intent.putExtra(MenuPickActivity.EXTRA_HAS_COMPLEX_FILTER, true);
        intent.putExtra(MenuPickActivity.EXTRA_COMPLEX_ONLY, false);
        startActivity(intent);
    }
}
