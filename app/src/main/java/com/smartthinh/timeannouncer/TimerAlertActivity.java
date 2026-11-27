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
    private TextView txtAlertTime;
    private TextView txtAlertName;
    private TextView txtAlertMessage;
    private Button btnRestart;
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

        txtAlertTitle = findViewById(R.id.txtTimerAlertTitle);
        txtAlertTime = findViewById(R.id.txtTimerAlertTime);
        txtAlertName = findViewById(R.id.txtTimerAlertName);
        txtAlertMessage = findViewById(R.id.txtTimerAlertMessage);
        btnRestart = findViewById(R.id.btnTimerAlertRestart);
        btnDismiss = findViewById(R.id.btnDismissTimerAlert);

        updateViews();

        btnRestart.setOnClickListener(v -> {
            TimerService.restartTimer(TimerAlertActivity.this, timerId);
            finish();
        });

        btnDismiss.setOnClickListener(v -> {
            TimerService.dismissAlert(TimerAlertActivity.this, timerId);
            finish();
        });
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            timerId = intent.getLongExtra(TimerService.EXTRA_TIMER_ID, -1);
            updateViews();
        }
    }

    private void updateViews() {
        TimerItem timer = TimerManagerHelper.getTimerById(this, timerId);
        if (timer != null) {
            String name = (timer.getName() == null || timer.getName().isEmpty()) ?
                    getString(R.string.btn_timer) : timer.getName();
            txtAlertName.setText(name + " (" + timer.getMinutes() + " " + getString(R.string.hint_timer_minutes).toLowerCase() + ")");
            if (!timer.getMessage().isEmpty()) {
                txtAlertMessage.setVisibility(View.VISIBLE);
                txtAlertMessage.setText(timer.getMessage());
            } else {
                txtAlertMessage.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onBackPressed() {
        TimerService.dismissAlert(this, timerId);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sActiveActivity == this) {
            sActiveActivity = null;
        }
    }
}
