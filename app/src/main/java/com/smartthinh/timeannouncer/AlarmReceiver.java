package com.smartthinh.timeannouncer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    public static final String ALARM_CHANNEL_ID = "alarm_alert_channel";

    private static PowerManager.WakeLock sWakeLock;

    public static synchronized void acquireWakeLock(Context context) {
        if (sWakeLock != null && sWakeLock.isHeld()) {
            return;
        }
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                sWakeLock = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK |
                        PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.ON_AFTER_RELEASE,
                        "TimeAnnouncer:AlarmWakeLock"
                );
                sWakeLock.acquire(10 * 60 * 1000L); // 10 minutes timeout
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire WakeLock", e);
        }
    }

    public static synchronized void releaseWakeLock() {
        if (sWakeLock != null && sWakeLock.isHeld()) {
            try {
                sWakeLock.release();
            } catch (Exception ignored) {}
            sWakeLock = null;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        long alarmId = intent.getLongExtra(AlarmManagerHelper.EXTRA_ALARM_ID, -1);
        Log.d(TAG, "Alarm triggered! Alarm ID=" + alarmId);

        // 1. Acquire WakeLock to turn screen on and keep CPU awake
        acquireWakeLock(context);

        // 2. Start AlarmService (Foreground Service) which plays ringtone, vibration, and TTS
        AlarmService.startAlarm(context, alarmId);
    }
}

