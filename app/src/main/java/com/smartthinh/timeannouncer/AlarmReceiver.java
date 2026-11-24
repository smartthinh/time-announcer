package com.smartthinh.timeannouncer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    public static final String ALARM_CHANNEL_ID = "alarm_alert_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        long alarmId = intent.getLongExtra(AlarmManagerHelper.EXTRA_ALARM_ID, -1);
        Log.d(TAG, "Alarm triggered! Alarm ID=" + alarmId);

        // Acquire WakeLock to ensure screen turns on and CPU stays awake
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                    PowerManager.ACQUIRE_CAUSES_WAKEUP |
                    PowerManager.ON_AFTER_RELEASE,
                    "TimeAnnouncer:AlarmWakeLock"
            );
            wakeLock.acquire(10 * 60 * 1000L); // 10 minutes timeout
        }

        // Notification channel for alarms
        createAlarmNotificationChannel(context);

        // Prepare full-screen Intent to display AlarmAlertActivity
        Intent alertIntent = new Intent(context, AlarmAlertActivity.class);
        alertIntent.putExtra(AlarmManagerHelper.EXTRA_ALARM_ID, alarmId);
        alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_NO_USER_ACTION);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                (int) (alarmId % Integer.MAX_VALUE),
                alertIntent,
                flags
        );

        AlarmItem alarm = AlarmManagerHelper.getAlarmById(context, alarmId);
        String title = context.getString(R.string.alarm_alert_title);
        String text = (alarm != null) ? alarm.getFormattedTime() : context.getString(R.string.app_name);
        if (alarm != null && !alarm.getMessage().isEmpty()) {
            text += " - " + alarm.getMessage();
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, ALARM_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) (alarmId % Integer.MAX_VALUE), builder.build());
        }

        // Launch activity directly
        try {
            context.startActivity(alertIntent);
        } catch (Exception e) {
            Log.e(TAG, "Could not start AlarmAlertActivity directly", e);
        }
    }

    private void createAlarmNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        ALARM_CHANNEL_ID,
                        context.getString(R.string.alarm_channel_name),
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription(context.getString(R.string.alarm_channel_desc));
                channel.setBypassDnd(true);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            }
        }
    }
}
