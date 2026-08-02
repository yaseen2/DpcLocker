package com.custom.dpclocker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("DPC Locker Protection");
        title.setTextSize(22);
        title.setPadding(0, 0, 0, 24);

        TextView description = new TextView(this);
        description.setText("This lightweight app locks Test DPC on your phone.\n\nTo activate, tap the button below and enable 'DPC Locker Protection' in Accessibility Services.\n\nTo unlock Test DPC in the future, connect via USB ADB.");
        description.setTextSize(16);
        description.setPadding(0, 0, 0, 32);

        Button btnEnable = new Button(this);
        btnEnable.setText("Enable Accessibility Service");
        btnEnable.setOnClickListener(this);

        layout.addView(title);
        layout.addView(description);
        layout.addView(btnEnable);

        setContentView(layout);
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }
}
