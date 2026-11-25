package com.smartthinh.timeannouncer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.util.Log;

public class TimeAnnouncementReceiver extends BroadcastReceiver {

    private static final String TAG = "TimeAnnouncementReceiver";
    public static final String ACTION_TRIGGER_ANNOUNCEMENT = "com.smartthinh.timeannouncer.ACTION_TRIGGER_ANNOUNCEMENT";

    private static PowerManager.WakeLock sWakeLock;

    public static synchronized void acquireWakeLock(Context context) {
        if (sWakeLock != null && sWakeLock.isHeld()) {
            return;
        }
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                sWakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "TimeAnnouncer:AnnouncementWakeLock"
                );
                sWakeLock.acquire(15 * 1000L); // 15 seconds max wake lock for time announcement
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
        Log.d(TAG, "Time announcement alarm received!");
        acquireWakeLock(context);

        SharedPreferences prefs = MainActivity.getSafeSharedPreferences(context);
        boolean autoSpeak = prefs.getBoolean("auto_speak", false);
        if (autoSpeak) {
            TimeAnnouncementService.announceNow(context);
        } else {
            TimeAnnouncementService.cancelAnnouncement(context);
            releaseWakeLock();
        }
    }
}
