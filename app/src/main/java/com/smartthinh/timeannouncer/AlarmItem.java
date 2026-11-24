package com.smartthinh.timeannouncer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Locale;

public class AlarmItem implements Serializable {

    private long id;
    private int hour; // 0 - 23
    private int minute; // 0 - 59
    private int snoozeCount; // Total allowed repeat/snooze count
    private int remainingSnoozeCount; // Remaining snooze count
    private int snoozeMinutes; // Minutes between repeats
    private String message; // Optional TTS announcement message
    private boolean[] daysOfWeek = new boolean[7]; // Mon=0, Tue=1, Wed=2, Thu=3, Fri=4, Sat=5, Sun=6
    private String ringtoneUri; // Empty string or URI string
    private String ringtoneTitle; // Title of ringtone
    private boolean isEnabled; // Enabled switch state

    public AlarmItem() {
        this.id = System.currentTimeMillis();
        Calendar now = Calendar.getInstance();
        this.hour = now.get(Calendar.HOUR_OF_DAY);
        this.minute = now.get(Calendar.MINUTE);
        this.snoozeCount = 3;
        this.remainingSnoozeCount = 3;
        this.snoozeMinutes = 5;
        this.message = "";
        this.ringtoneUri = "";
        this.ringtoneTitle = "";
        this.isEnabled = true;

        // Default: select current day of week
        int calDow = now.get(Calendar.DAY_OF_WEEK);
        int todayIndex = (calDow == Calendar.SUNDAY) ? 6 : (calDow - 2);
        this.daysOfWeek[todayIndex] = true;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = Math.max(0, Math.min(23, hour));
    }

    public int getMinute() {
        return minute;
    }

    public void setMinute(int minute) {
        this.minute = Math.max(0, Math.min(59, minute));
    }

    public int getSnoozeCount() {
        return snoozeCount;
    }

    public void setSnoozeCount(int snoozeCount) {
        this.snoozeCount = Math.max(0, snoozeCount);
    }

    public int getRemainingSnoozeCount() {
        return remainingSnoozeCount;
    }

    public void setRemainingSnoozeCount(int remainingSnoozeCount) {
        this.remainingSnoozeCount = Math.max(0, remainingSnoozeCount);
    }

    public int getSnoozeMinutes() {
        return snoozeMinutes;
    }

    public void setSnoozeMinutes(int snoozeMinutes) {
        this.snoozeMinutes = Math.max(1, snoozeMinutes);
    }

    public String getMessage() {
        return message == null ? "" : message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message.trim();
    }

    public boolean[] getDaysOfWeek() {
        return daysOfWeek;
    }

    public void setDaysOfWeek(boolean[] daysOfWeek) {
        if (daysOfWeek != null && daysOfWeek.length == 7) {
            System.arraycopy(daysOfWeek, 0, this.daysOfWeek, 0, 7);
        }
    }

    public String getRingtoneUri() {
        return ringtoneUri == null ? "" : ringtoneUri;
    }

    public void setRingtoneUri(String ringtoneUri) {
        this.ringtoneUri = ringtoneUri;
    }

    public String getRingtoneTitle() {
        return ringtoneTitle == null ? "" : ringtoneTitle;
    }

    public void setRingtoneTitle(String ringtoneTitle) {
        this.ringtoneTitle = ringtoneTitle;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public boolean isEveryDay() {
        for (boolean b : daysOfWeek) {
            if (!b) return false;
        }
        return true;
    }

    public boolean hasAnyDay() {
        for (boolean b : daysOfWeek) {
            if (b) return true;
        }
        return false;
    }

    public String getFormattedTime() {
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    public String getFormattedDays(Context context) {
        if (isEveryDay()) {
            return context.getString(R.string.alarm_days_everyday);
        }
        if (!hasAnyDay()) {
            return context.getString(R.string.alarm_days_once);
        }
        String[] dayNames = {
                context.getString(R.string.day_mon_short),
                context.getString(R.string.day_tue_short),
                context.getString(R.string.day_wed_short),
                context.getString(R.string.day_thu_short),
                context.getString(R.string.day_fri_short),
                context.getString(R.string.day_sat_short),
                context.getString(R.string.day_sun_short)
        };
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (daysOfWeek[i]) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(dayNames[i]);
            }
        }
        return sb.toString();
    }

    public long getNextTriggerTimeMillis() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (!hasAnyDay()) {
            if (target.getTimeInMillis() <= now.getTimeInMillis() + 1000) {
                target.add(Calendar.DAY_OF_YEAR, 1);
            }
            return target.getTimeInMillis();
        }

        int calDow = now.get(Calendar.DAY_OF_WEEK);
        int todayIndex = (calDow == Calendar.SUNDAY) ? 6 : (calDow - 2);

        if (daysOfWeek[todayIndex] && target.getTimeInMillis() > now.getTimeInMillis() + 1000) {
            return target.getTimeInMillis();
        }

        for (int offset = 1; offset <= 7; offset++) {
            int nextIndex = (todayIndex + offset) % 7;
            if (daysOfWeek[nextIndex]) {
                target.add(Calendar.DAY_OF_YEAR, offset);
                return target.getTimeInMillis();
            }
        }

        return target.getTimeInMillis();
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("hour", hour);
            obj.put("minute", minute);
            obj.put("snoozeCount", snoozeCount);
            obj.put("remainingSnoozeCount", remainingSnoozeCount);
            obj.put("snoozeMinutes", snoozeMinutes);
            obj.put("message", message);
            JSONArray daysArr = new JSONArray();
            for (boolean b : daysOfWeek) {
                daysArr.put(b);
            }
            obj.put("daysOfWeek", daysArr);
            obj.put("ringtoneUri", ringtoneUri);
            obj.put("ringtoneTitle", ringtoneTitle);
            obj.put("isEnabled", isEnabled);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static AlarmItem fromJson(JSONObject obj) {
        AlarmItem item = new AlarmItem();
        try {
            item.setId(obj.optLong("id", System.currentTimeMillis()));
            item.setHour(obj.optInt("hour", 0));
            item.setMinute(obj.optInt("minute", 0));
            item.setSnoozeCount(obj.optInt("snoozeCount", 3));
            item.setRemainingSnoozeCount(obj.optInt("remainingSnoozeCount", item.getSnoozeCount()));
            item.setSnoozeMinutes(obj.optInt("snoozeMinutes", 5));
            item.setMessage(obj.optString("message", ""));
            JSONArray daysArr = obj.optJSONArray("daysOfWeek");
            if (daysArr != null && daysArr.length() == 7) {
                boolean[] days = new boolean[7];
                for (int i = 0; i < 7; i++) {
                    days[i] = daysArr.optBoolean(i, false);
                }
                item.setDaysOfWeek(days);
            }
            item.setRingtoneUri(obj.optString("ringtoneUri", ""));
            item.setRingtoneTitle(obj.optString("ringtoneTitle", ""));
            item.setEnabled(obj.optBoolean("isEnabled", true));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return item;
    }
}
