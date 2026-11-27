package com.smartthinh.timeannouncer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TimerManagerHelper {

    private static final String TAG = "TimerManagerHelper";
    public static final String PREF_KEY_TIMERS = "saved_timers_list";

    public static List<TimerItem> loadTimers(Context context) {
        List<TimerItem> list = new ArrayList<>();
        SharedPreferences prefs = MainActivity.getSafeSharedPreferences(context);
        String jsonStr = prefs.getString(PREF_KEY_TIMERS, null);
        if (jsonStr != null && !jsonStr.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    list.add(TimerItem.fromJson(obj));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse timers JSON", e);
            }
        }
        return list;
    }

    public static void saveTimers(Context context, List<TimerItem> timers) {
        JSONArray arr = new JSONArray();
        for (TimerItem item : timers) {
            arr.put(item.toJson());
        }
        SharedPreferences prefs = MainActivity.getSafeSharedPreferences(context);
        prefs.edit().putString(PREF_KEY_TIMERS, arr.toString()).apply();
    }

    public static TimerItem getTimerById(Context context, long id) {
        List<TimerItem> list = loadTimers(context);
        for (TimerItem item : list) {
            if (item.getId() == id) {
                return item;
            }
        }
        return null;
    }

    public static TimerItem getFinishedTimer(Context context) {
        List<TimerItem> list = loadTimers(context);
        for (TimerItem item : list) {
            if (item.getState() == TimerItem.STATE_FINISHED) {
                return item;
            }
        }
        return null;
    }

    public static TimerItem getActiveRunningTimer(Context context) {
        List<TimerItem> list = loadTimers(context);
        for (TimerItem item : list) {
            if (item.getState() == TimerItem.STATE_RUNNING) {
                return item;
            }
        }
        for (TimerItem item : list) {
            if (item.getState() == TimerItem.STATE_PAUSED) {
                return item;
            }
        }
        return null;
    }

    public static void saveOrUpdateTimer(Context context, TimerItem timer) {
        List<TimerItem> list = loadTimers(context);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == timer.getId()) {
                list.set(i, timer);
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(timer);
        }
        saveTimers(context, list);
    }

    public static void deleteTimers(Context context, List<Long> idsToDelete) {
        List<TimerItem> list = loadTimers(context);
        Iterator<TimerItem> iterator = list.iterator();
        while (iterator.hasNext()) {
            TimerItem item = iterator.next();
            if (idsToDelete.contains(item.getId())) {
                iterator.remove();
            }
        }
        saveTimers(context, list);
    }
}
