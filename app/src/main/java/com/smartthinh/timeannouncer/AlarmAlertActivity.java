package com.smartthinh.timeannouncer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class AlarmAlertActivity extends Activity {

    private static AlarmAlertActivity sActiveActivity;

    private TextView txtAlarmTime;
    private TextView txtAlarmMessage;
    private TextView txtSnoozeInfo;
    private Button btnSnooze;
    private Button btnDismiss;

    private AlarmItem alarm;
    private long alarmId = -1;

    public static void closeActivity() {
        if (sActiveActivity != null && !sActiveActivity.isFinishing()) {
            sActiveActivity.finish();
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = MainActivity.getSafeSharedPreferences(newBase);
        String lang = prefs.getString("app_language", "en");
        super.attachBaseContext(MainActivity.updateLocaleContext(newBase, lang));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must be called before super.onCreate and setContentView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        );

        super.onCreate(savedInstanceState);
        sActiveActivity = this;

        setContentView(R.layout.dialog_alarm_alert);
        setFinishOnTouchOutside(false);

        alarmId = getIntent().getLongExtra(AlarmManagerHelper.EXTRA_ALARM_ID, -1);
        alarm = AlarmManagerHelper.getAlarmById(this, alarmId);

        txtAlarmTime = findViewById(R.id.txtAlertTime);
        txtAlarmMessage = findViewById(R.id.txtAlertMessage);
        txtSnoozeInfo = findViewById(R.id.txtAlertSnoozeInfo);
        btnSnooze = findViewById(R.id.btnAlertSnooze);
        btnDismiss = findViewById(R.id.btnAlertDismiss);

        if (alarm != null) {
            txtAlarmTime.setText(alarm.getFormattedTime());
            if (!alarm.getMessage().isEmpty()) {
                txtAlarmMessage.setVisibility(View.VISIBLE);
                txtAlarmMessage.setText(alarm.getMessage());
            } else {
                txtAlarmMessage.setVisibility(View.GONE);
            }

            int rem = alarm.getRemainingSnoozeCount();
            if (rem > 0) {
                txtSnoozeInfo.setText(getString(R.string.alarm_snooze_remaining, rem, alarm.getSnoozeMinutes()));
                txtSnoozeInfo.setVisibility(View.VISIBLE);
            } else {
                txtSnoozeInfo.setText(getString(R.string.alarm_snooze_none_left));
                txtSnoozeInfo.setVisibility(View.VISIBLE);
            }
        } else {
            txtAlarmTime.setText(getString(R.string.alarm_alert_title));
            txtAlarmMessage.setVisibility(View.GONE);
            txtSnoozeInfo.setVisibility(View.GONE);
        }

        btnSnooze.setOnClickListener(v -> {
            AlarmService.snoozeAlarm(this, alarmId);
            finish();
        });

        btnDismiss.setOnClickListener(v -> {
            AlarmService.dismissAlarm(this, alarmId);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        if (sActiveActivity == this) {
            sActiveActivity = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Prevent accidental dismiss: back button acts as snooze
        AlarmService.snoozeAlarm(this, alarmId);
        finish();
    }
}
