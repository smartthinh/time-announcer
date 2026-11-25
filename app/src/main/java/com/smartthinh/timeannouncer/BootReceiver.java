package com.smartthinh.timeannouncer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = (intent != null) ? intent.getAction() : null;
        Log.d(TAG, "BootReceiver triggered with action: " + action);

        // 1. Reschedule all enabled alarms across reboots, quickboot, time updates
        try {
            AlarmManagerHelper.rescheduleAllEnabledAlarms(context);
            Log.d(TAG, "Successfully rescheduled all enabled alarms.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to reschedule alarms on boot", e);
        }

        // 2. Reschedule and restart Time Announcement Service if auto_speak is enabled
        try {
            SharedPreferences prefs = MainActivity.getSafeSharedPreferences(context);
            boolean autoSpeak = prefs.getBoolean("auto_speak", false);
            if (autoSpeak) {
                Log.d(TAG, "Auto speak is enabled, scheduling announcement and starting service...");
                TimeAnnouncementService.scheduleNextAnnouncement(context);
                TimeAnnouncementService.startAnnouncerService(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore TimeAnnouncementService on boot", e);
        }
    }
}
