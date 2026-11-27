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

        // 3. Reschedule running timers across reboots
        try {
            java.util.List<TimerItem> timers = TimerManagerHelper.loadTimers(context);
            for (TimerItem timer : timers) {
                if (timer.getState() == TimerItem.STATE_RUNNING) {
                    long remaining = timer.getEndTimestamp() - System.currentTimeMillis();
                    if (remaining > 0) {
                        timer.setRemainingMillis(remaining);
                        TimerManagerHelper.saveOrUpdateTimer(context, timer);
                        TimerService.resumeTimer(context, timer.getId());
                        Log.d(TAG, "Resumed running timer on boot: " + timer.getName());
                    } else {
                        TimerService.onTimerAlarmTriggered(context, timer.getId());
                        Log.d(TAG, "Triggered expired timer on boot: " + timer.getName());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore timers on boot", e);
        }
    }
}
