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

public class TimerAlertActivity extends Activity {

    private static TimerAlertActivity sActiveActivity;

    private TextView txtAlertTitle;
    private TextView txtAlertName;
    private TextView txtAlertMessage;
    private Button btnDismiss;

    private long timerId = -1;

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

        setContentView(R.layout.dialog_timer_alert);
        setFinishOnTouchOutside(false);

        timerId = getIntent().getLongExtra(TimerService.EXTRA_TIMER_ID, -1);
        TimerItem timer = TimerManagerHelper.getTimerById(this, timerId);

        txtAlertTitle = findViewById(R.id.txtTimerAlertTitle);
        txtAlertName = findViewById(R.id.txtTimerAlertName);
        txtAlertMessage = findViewById(R.id.txtTimerAlertMessage);
        btnDismiss = findViewById(R.id.btnDismissTimerAlert);

        if (timer != null) {
            txtAlertName.setText(timer.getName());
            if (!timer.getMessage().isEmpty()) {
                txtAlertMessage.setVisibility(View.VISIBLE);
                txtAlertMessage.setText(timer.getMessage());
            } else {
                txtAlertMessage.setVisibility(View.GONE);
            }
        }

        btnDismiss.setOnClickListener(v -> {
            TimerService.dismissAlert(TimerAlertActivity.this, timerId);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sActiveActivity == this) {
            sActiveActivity = null;
        }
    }
}
