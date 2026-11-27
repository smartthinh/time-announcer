package com.smartthinh.timeannouncer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Locale;

public class TimerItem implements Serializable {

    public static final int STATE_IDLE = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_FINISHED = 3;

    private long id;
    private String name;
    private int minutes; // total minutes configured
    private String message;
    private boolean isVibrate;
    private boolean isSoundEnabled;
    private String soundPath;
    private String soundName;
    private boolean isCustomSound;

    // Runtime countdown fields
    private int state = STATE_IDLE;
    private long remainingMillis = 0;
    private long endTimestamp = 0;

    public TimerItem() {
        this.id = System.currentTimeMillis();
        this.name = "";
        this.minutes = 5; // default 5 minutes
        this.message = "";
        this.isVibrate = true;
        this.isSoundEnabled = true;
        this.soundPath = "sounds/bell.wav";
        this.soundName = "bell";
        this.isCustomSound = false;
        this.state = STATE_IDLE;
        this.remainingMillis = this.minutes * 60 * 1000L;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return (name == null || name.trim().isEmpty()) ? "Hẹn giờ " + minutes + " phút" : name.trim();
    }

    public String getRawName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMinutes() {
        return Math.max(1, minutes);
    }

    public void setMinutes(int minutes) {
        this.minutes = Math.max(1, minutes);
        if (state == STATE_IDLE) {
            this.remainingMillis = this.minutes * 60 * 1000L;
        }
    }

    public String getMessage() {
        return message == null ? "" : message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isVibrate() {
        return isVibrate;
    }

    public void setVibrate(boolean vibrate) {
        isVibrate = vibrate;
    }

    public boolean isSoundEnabled() {
        return isSoundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        isSoundEnabled = soundEnabled;
    }

    public String getSoundPath() {
        return soundPath == null ? "sounds/bell.wav" : soundPath;
    }

    public void setSoundPath(String soundPath) {
        this.soundPath = soundPath;
    }

    public String getSoundName() {
        return (soundName == null || soundName.isEmpty()) ? "bell" : soundName;
    }

    public void setSoundName(String soundName) {
        this.soundName = soundName;
    }

    public boolean isCustomSound() {
        return isCustomSound;
    }

    public void setCustomSound(boolean customSound) {
        isCustomSound = customSound;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public long getRemainingMillis() {
        if (state == STATE_RUNNING) {
            long rem = endTimestamp - System.currentTimeMillis();
            return Math.max(0, rem);
        }
        return remainingMillis;
    }

    public void setRemainingMillis(long remainingMillis) {
        this.remainingMillis = Math.max(0, remainingMillis);
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public String getFormattedRemainingTime() {
        long millis = getRemainingMillis();
        long totalSeconds = (millis + 999) / 1000;
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", m, s);
        }
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("name", name);
            obj.put("minutes", minutes);
            obj.put("message", message);
            obj.put("isVibrate", isVibrate);
            obj.put("isSoundEnabled", isSoundEnabled);
            obj.put("soundPath", soundPath);
            obj.put("soundName", soundName);
            obj.put("isCustomSound", isCustomSound);
            obj.put("state", state);
            obj.put("remainingMillis", remainingMillis);
            obj.put("endTimestamp", endTimestamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static TimerItem fromJson(JSONObject obj) {
        TimerItem item = new TimerItem();
        try {
            item.setId(obj.optLong("id", System.currentTimeMillis()));
            item.setName(obj.optString("name", ""));
            item.setMinutes(obj.optInt("minutes", 5));
            item.setMessage(obj.optString("message", ""));
            item.setVibrate(obj.optBoolean("isVibrate", true));
            item.setSoundEnabled(obj.optBoolean("isSoundEnabled", true));
            item.setSoundPath(obj.optString("soundPath", "sounds/bell.wav"));
            item.setSoundName(obj.optString("soundName", "bell"));
            item.setCustomSound(obj.optBoolean("isCustomSound", false));
            item.setState(obj.optInt("state", STATE_IDLE));
            item.setRemainingMillis(obj.optLong("remainingMillis", item.getMinutes() * 60 * 1000L));
            item.setEndTimestamp(obj.optLong("endTimestamp", 0));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return item;
    }
}
