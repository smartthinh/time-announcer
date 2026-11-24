package com.smartthinh.timeannouncer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AlarmManagerHelper {

    private static final String TAG = "AlarmManagerHelper";
    public static final String ACTION_ALARM_TRIGGER = "com.smartthinh.timeannouncer.ACTION_ALARM_TRIGGER";
    public static final String EXTRA_ALARM_ID = "alarm_id";
    public static final String PREF_KEY_ALARMS = "saved_alarms_list";

    public static List<AlarmItem> loadAlarms(Context context) {
        List<AlarmItem> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString(PREF_KEY_ALARMS, null);
        if (jsonStr != null && !jsonStr.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    list.add(AlarmItem.fromJson(obj));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse alarms JSON", e);
            }
        }
        return list;
    }

    public static void saveAlarms(Context context, List<AlarmItem> alarms) {
        JSONArray arr = new JSONArray();
        for (AlarmItem item : alarms) {
            arr.put(item.toJson());
        }
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_KEY_ALARMS, arr.toString()).apply();
    }

    public static AlarmItem getAlarmById(Context context, long id) {
        List<AlarmItem> list = loadAlarms(context);
        for (AlarmItem item : list) {
            if (item.getId() == id) {
                return item;
            }
        }
        return null;
    }

    public static void saveOrUpdateAlarm(Context context, AlarmItem alarm) {
        List<AlarmItem> list = loadAlarms(context);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == alarm.getId()) {
                list.set(i, alarm);
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(alarm);
        }
        saveAlarms(context, list);

        if (alarm.isEnabled()) {
            scheduleAlarm(context, alarm);
        } else {
            cancelAlarm(context, alarm);
        }
    }

    public static void deleteAlarms(Context context, List<Long> idsToDelete) {
        List<AlarmItem> list = loadAlarms(context);
        Iterator<AlarmItem> iterator = list.iterator();
        while (iterator.hasNext()) {
            AlarmItem item = iterator.next();
            if (idsToDelete.contains(item.getId())) {
                cancelAlarm(context, item);
                iterator.remove();
            }
        }
        saveAlarms(context, list);
    }

    public static void scheduleAlarm(Context context, AlarmItem alarm) {
        long triggerTime = alarm.getNextTriggerTimeMillis();
        scheduleExact(context, alarm, triggerTime);
    }

    public static void scheduleSnooze(Context context, AlarmItem alarm, long triggerTime) {
        scheduleExact(context, alarm, triggerTime);
    }

    private static void scheduleExact(Context context, AlarmItem alarm, long triggerTime) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_ALARM_TRIGGER);
        intent.putExtra(EXTRA_ALARM_ID, alarm.getId());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        int requestCode = (int) (alarm.getId() % Integer.MAX_VALUE);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, flags);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Intent showIntent = new Intent(context, MainActivity.class);
                PendingIntent showPi = PendingIntent.getActivity(context, requestCode, showIntent, flags);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerTime, showPi), pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            }
            Log.d(TAG, "Scheduled alarm ID=" + alarm.getId() + " at " + triggerTime);
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException scheduling exact alarm", se);
            try {
                am.set(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            } catch (Exception e) {
                Log.e(TAG, "Fallback set alarm failed", e);
            }
        }
    }

    public static void cancelAlarm(Context context, AlarmItem alarm) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_ALARM_TRIGGER);
        intent.putExtra(EXTRA_ALARM_ID, alarm.getId());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        int requestCode = (int) (alarm.getId() % Integer.MAX_VALUE);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, flags);
        am.cancel(pi);
        Log.d(TAG, "Cancelled alarm ID=" + alarm.getId());
    }

    public static void rescheduleAllEnabledAlarms(Context context) {
        List<AlarmItem> alarms = loadAlarms(context);
        for (AlarmItem alarm : alarms) {
            if (alarm.isEnabled()) {
                // Reset remaining snooze count on reboot or restart
                alarm.setRemainingSnoozeCount(alarm.getSnoozeCount());
                scheduleAlarm(context, alarm);
            }
        }
        saveAlarms(context, alarms);
    }
}
