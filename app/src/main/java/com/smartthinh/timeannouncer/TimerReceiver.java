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
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "TimeAnnouncer::TimerWakeLock"
                );
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10 * 60 * 1000L); // 10 minutes max
        }
    }

    public static synchronized void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }
    }
}
