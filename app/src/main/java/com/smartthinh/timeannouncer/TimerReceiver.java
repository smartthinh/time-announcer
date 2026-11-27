package com.smartthinh.timeannouncer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class TimerReceiver extends BroadcastReceiver {

    public static final String ACTION_TIMER_TRIGGER = "com.smartthinh.timeannouncer.ACTION_TIMER_TRIGGER";
    public static final String EXTRA_TIMER_ID = "timer_id";

    private static PowerManager.WakeLock wakeLock;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1);
        if (timerId == -1) return;

        acquireWakeLock(context);
        TimerService.onTimerAlarmTriggered(context, timerId);
    }

    public static synchronized void acquireWakeLock(Context context) {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK |
                        PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.ON_AFTER_RELEASE,
                        "TimeAnnouncer:TimerWakeLock"
                );
                wakeLock.acquire(60 * 1000L); // 1 minute max
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }
    }
}
