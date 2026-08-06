package com.afwsamples.testdpc;

import android.app.Activity;
import android.os.Bundle;

public class SetupManagementActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int lockEnabled = android.provider.Settings.Global.getInt(getContentResolver(), "dpclocker_enabled", 1);
        if (lockEnabled == 1) {
            android.widget.Toast.makeText(this, "Protection Active! Connect via USB ADB to unlock.", android.widget.Toast.LENGTH_LONG).show();
            finish();
            super.onCreate(null);
            return;
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().add(R.id.container,
                    new SetupManagementFragment(),
                    SetupManagementFragment.FRAGMENT_TAG).commit();
        }
    }
}
