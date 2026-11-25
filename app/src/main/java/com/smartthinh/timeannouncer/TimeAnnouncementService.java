package com.smartthinh.timeannouncer;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

public class TimeAnnouncementService extends Service implements TextToSpeech.OnInitListener {

    private static final String TAG = "TimeAnnouncerService";
    private static final String CHANNEL_ID = "TimeAnnouncerChannel";
    public static final String ACTION_ANNOUNCE_NOW = "com.smartthinh.timeannouncer.ACTION_ANNOUNCE_NOW";

    private TextToSpeech tts;
    private boolean isTtsReady = false;

    private int interval = 60;
    private boolean[] hours = new boolean[24];
    private String enginePackage = "";
    private String voiceName = "";
    private float speechRate = 1.0f;
    private float speechPitch = 1.0f;
    private int speechVolume = 50;
    private int audioStreamIndex = 0;

    // Announcement sound and vibration configuration
    private boolean isPlaySoundEnabled = false;
    private boolean isCustomSound = false;
    private String soundPath = "";
    private boolean isVibrateEnabled = false;

    // App language ("en" or "vi")
    private String appLanguage = "en";

    private MediaPlayer currentPlayer;
    private Handler handler;
    private Runnable checkTimeRunnable;
    private int lastAnnouncedMinute = -1;
    private int lastAnnouncedHour = -1;
    private PowerManager.WakeLock wakeLock;

    public static void startAnnouncerService(Context context) {
        Intent intent = new Intent(context, TimeAnnouncementService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start TimeAnnouncementService", e);
        }
    }

    public static void stopAnnouncerService(Context context) {
        Intent intent = new Intent(context, TimeAnnouncementService.class);
        try {
            context.stopService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop TimeAnnouncementService", e);
        }
    }

    public static void announceNow(Context context) {
        Intent intent = new Intent(context, TimeAnnouncementService.class);
        intent.setAction(ACTION_ANNOUNCE_NOW);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send announceNow to TimeAnnouncementService", e);
        }
    }

    public static long getNextAnnouncementTimeMillis(int interval, boolean[] hours) {
        if (interval <= 0) interval = 60;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        int currentMinute = cal.get(Calendar.MINUTE);
        int remainder = currentMinute % interval;
        int minutesToAdd = interval - remainder;
        cal.add(Calendar.MINUTE, minutesToAdd);

        for (int i = 0; i < 48 * 60 / interval; i++) {
            int h = cal.get(Calendar.HOUR_OF_DAY);
            if (hours == null || hours.length < 24 || hours[h]) {
                return cal.getTimeInMillis();
            }
            cal.add(Calendar.MINUTE, interval);
        }
        return cal.getTimeInMillis();
    }

    public static void scheduleNextAnnouncement(Context context) {
        SharedPreferences prefs = MainActivity.getSafeSharedPreferences(context);
        boolean autoSpeak = prefs.getBoolean("auto_speak", false);
        if (!autoSpeak) {
            cancelAnnouncement(context);
            return;
        }

        int interval = prefs.getInt("interval", 60);
        boolean[] hours = new boolean[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = prefs.getBoolean("hour_" + i, true);
        }

        long nextTime = getNextAnnouncementTimeMillis(interval, hours);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, TimeAnnouncementReceiver.class);
        intent.setAction(TimeAnnouncementReceiver.ACTION_TRIGGER_ANNOUNCEMENT);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pi = PendingIntent.getBroadcast(context, 7777, intent, flags);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Intent showIntent = new Intent(context, MainActivity.class);
                PendingIntent showPi = PendingIntent.getActivity(context, 7777, showIntent, flags);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(nextTime, showPi), pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, nextTime, pi);
            }
            Log.d(TAG, "Scheduled next time announcement via AlarmManager at " + nextTime);
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException scheduling exact announcement alarm", se);
            try {
                am.set(AlarmManager.RTC_WAKEUP, nextTime, pi);
            } catch (Exception e) {
                Log.e(TAG, "Fallback announcement alarm failed", e);
            }
        }
    }

    public static void cancelAnnouncement(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, TimeAnnouncementReceiver.class);
        intent.setAction(TimeAnnouncementReceiver.ACTION_TRIGGER_ANNOUNCEMENT);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pi = PendingIntent.getBroadcast(context, 7777, intent, flags);
        am.cancel(pi);
        Log.d(TAG, "Cancelled time announcement alarm in AlarmManager");
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = MainActivity.getSafeSharedPreferences(newBase);
        String lang = prefs.getString("app_language", "en");
        super.attachBaseContext(MainActivity.updateLocaleContext(newBase, lang));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Immediately create channel and enter foreground (Android 8+ requires startForeground quickly)
        createNotificationChannel();
        Notification notification = buildForegroundNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, notification);
        }

        // 2. Load settings and init TTS
        loadSettings();
        initTts();

        // 3. Keep partial wake lock for reliability
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimeAnnouncer::WakeLock");
            try {
                wakeLock.acquire(24 * 60 * 60 * 1000L);
            } catch (Exception ignored) {}
        }

        // 4. Start 1-second check loop for foreground precision
        handler = new Handler(Looper.getMainLooper());
        checkTimeRunnable = new Runnable() {
            @Override
            public void run() {
                checkAndAnnounce();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(checkTimeRunnable);

        // 5. Ensure AlarmManager exact trigger is scheduled for deep sleep/doze mode
        scheduleNextAnnouncement(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        loadSettings();
        applyTtsSettings();

        if (intent != null && ACTION_ANNOUNCE_NOW.equals(intent.getAction())) {
            Calendar calendar = Calendar.getInstance();
            if (calendar.get(Calendar.SECOND) >= 55) {
                calendar.add(Calendar.SECOND, 10);
            }
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);
            if (hours[hour]) {
                performAnnouncement(hour, minute);
            }
            scheduleNextAnnouncement(this);
        }

        return START_STICKY;
    }

    private void loadSettings() {
        SharedPreferences prefs = MainActivity.getSafeSharedPreferences(this);
        interval = prefs.getInt("interval", 60);
        for (int i = 0; i < 24; i++) {
            hours[i] = prefs.getBoolean("hour_" + i, true);
        }
        String newEngine = prefs.getString("tts_engine", "");
        if (!newEngine.equals(enginePackage)) {
            enginePackage = newEngine;
            if (tts != null) {
                initTts();
            }
        }
        voiceName = prefs.getString("tts_voice", "");
        speechRate = prefs.getFloat("speech_rate", 1.0f);
        speechPitch = prefs.getFloat("speech_pitch", 1.0f);
        speechVolume = prefs.getInt("speech_volume", 50);
        audioStreamIndex = prefs.getInt("audio_stream_index", 0);

        isPlaySoundEnabled = prefs.getBoolean("play_sound", false);
        isCustomSound = prefs.getBoolean("sound_is_custom", false);
        soundPath = prefs.getString("sound_path", "sounds/bell.wav");
        isVibrateEnabled = prefs.getBoolean("announce_vibrate", false);

        appLanguage = prefs.getString("app_language", "en");
    }

    private void initTts() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {}
        }
        isTtsReady = false;

        try {
            if (enginePackage != null && !enginePackage.isEmpty()) {
                tts = new TextToSpeech(this, this, enginePackage);
            } else {
                tts = new TextToSpeech(this, this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to instantiate TextToSpeech", e);
        }
    }

    private Locale getTargetLocale() {
        return new Locale(appLanguage);
    }

    private void applyTtsSettings() {
        if (!isTtsReady || tts == null) return;

        try {
            tts.setSpeechRate(speechRate);
            tts.setPitch(speechPitch);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (!voiceName.isEmpty()) {
                    Set<Voice> voices = tts.getVoices();
                    if (voices != null) {
                        for (Voice v : voices) {
                            if (v.getName().equals(voiceName)) {
                                tts.setVoice(v);
                                break;
                            }
                        }
                    }
                } else {
                    tts.setLanguage(getTargetLocale());
                }

                int streamType = MainActivity.getStreamTypeFromIndex(audioStreamIndex);
                AudioAttributes.Builder aab = new AudioAttributes.Builder();
                switch (streamType) {
                    case AudioManager.STREAM_RING:
                        aab.setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                           .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION);
                        break;
                    case AudioManager.STREAM_ALARM:
                        aab.setUsage(AudioAttributes.USAGE_ALARM)
                           .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION);
                        break;
                    case AudioManager.STREAM_NOTIFICATION:
                        aab.setUsage(AudioAttributes.USAGE_NOTIFICATION)
                           .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION);
                        break;
                    default:
                        if (Build.VERSION.SDK_INT >= 26 && streamType == AudioManager.STREAM_ACCESSIBILITY) {
                            aab.setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY);
                        } else {
                            aab.setUsage(AudioAttributes.USAGE_MEDIA);
                        }
                        aab.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
                        break;
                }
                tts.setAudioAttributes(aab.build());
            }

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {}

                @Override
                public void onDone(String utteranceId) {
                    TimeAnnouncementReceiver.releaseWakeLock();
                }

                @Override
                public void onError(String utteranceId) {
                    TimeAnnouncementReceiver.releaseWakeLock();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply TTS settings", e);
        }
    }

    private void checkAndAnnounce() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        if (second <= 3 && (minute != lastAnnouncedMinute || hour != lastAnnouncedHour)) {
            if (hours[hour] && (minute % interval == 0)) {
                performAnnouncement(hour, minute);
                scheduleNextAnnouncement(this);
            }
        }
    }

    private void performAnnouncement(int hour, int minute) {
        if (hour == lastAnnouncedHour && minute == lastAnnouncedMinute) {
            return;
        }
        lastAnnouncedHour = hour;
        lastAnnouncedMinute = minute;

        if (isVibrateEnabled) {
            vibrateAnnouncement();
        }

        if (!isPlaySoundEnabled || soundPath == null || soundPath.isEmpty()) {
            speakTimeTts(hour, minute);
            return;
        }

        stopCurrentPlayer();

        try {
            currentPlayer = new MediaPlayer();
            MainActivity.applyAudioAttributesToPlayer(currentPlayer, audioStreamIndex);

            if (isCustomSound) {
                currentPlayer.setDataSource(this, Uri.parse(soundPath));
            } else {
                AssetFileDescriptor afd = getAssets().openFd(soundPath);
                currentPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            }

            float vol = Math.max(0.0f, Math.min(1.0f, speechVolume / 100.0f));
            currentPlayer.setVolume(vol, vol);

            currentPlayer.setOnCompletionListener(mp -> {
                stopCurrentPlayer();
                speakTimeTts(hour, minute);
            });

            currentPlayer.setOnErrorListener((mp, what, extra) -> {
                stopCurrentPlayer();
                speakTimeTts(hour, minute);
                return true;
            });

            currentPlayer.prepare();
            currentPlayer.start();

            handler.postDelayed(() -> {
                if (currentPlayer != null && currentPlayer.isPlaying()) {
                    stopCurrentPlayer();
                    speakTimeTts(hour, minute);
                }
            }, 8000);

        } catch (Exception e) {
            stopCurrentPlayer();
            speakTimeTts(hour, minute);
        }
    }

    private void stopCurrentPlayer() {
        if (currentPlayer != null) {
            try {
                currentPlayer.stop();
                currentPlayer.release();
            } catch (Exception ignored) {}
            currentPlayer = null;
        }
    }

    private void vibrateAnnouncement() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 500, 250, 500};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    int usage = AudioAttributes.USAGE_NOTIFICATION;
                    if (audioStreamIndex == 2) {
                        usage = AudioAttributes.USAGE_ALARM;
                    } else if (audioStreamIndex == 1) {
                        usage = AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
                    }
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    VibrationEffect effect = VibrationEffect.createWaveform(pattern, -1);
                    vibrator.vibrate(effect, audioAttributes);
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error vibrating for time announcement", e);
        }
    }

    private void speakTimeTts(int hour, int minute) {
        if (!isTtsReady || tts == null) {
            TimeAnnouncementReceiver.releaseWakeLock();
            return;
        }

        applyTtsSettings();

        String speech;
        if (minute == 0) {
            speech = getString(R.string.speech_exact_hour, hour);
        } else {
            speech = getString(R.string.speech_hour_minute, hour, minute);
        }

        int streamType = MainActivity.getStreamTypeFromIndex(audioStreamIndex);
        float vol = Math.max(0.0f, Math.min(1.0f, speechVolume / 100.0f));

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle params = new Bundle();
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, streamType);
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol);
                tts.speak(speech, TextToSpeech.QUEUE_FLUSH, params, "TimeAnnouncement");
            } else {
                java.util.HashMap<String, String> params = new java.util.HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(streamType));
                params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, String.valueOf(vol));
                tts.speak(speech, TextToSpeech.QUEUE_FLUSH, params);
            }
        } catch (Exception e) {
            Log.e(TAG, "TTS speak failed", e);
            TimeAnnouncementReceiver.releaseWakeLock();
        }

        // Safety timeout to guarantee wake lock release if callback is missed
        if (handler != null) {
            handler.postDelayed(TimeAnnouncementReceiver::releaseWakeLock, 15000);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                tts.setLanguage(getTargetLocale());
                isTtsReady = true;
                applyTtsSettings();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing TTS language", e);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_desc));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildForegroundNotification() {
        Intent notifyIntent = new Intent(this, MainActivity.class);
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, piFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        SharedPreferences prefs = MainActivity.getSafeSharedPreferences(this);
        if (prefs.getBoolean("auto_speak", false)) {
            scheduleNextAnnouncement(this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCurrentPlayer();
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Exception ignored) {}
        if (handler != null && checkTimeRunnable != null) {
            handler.removeCallbacks(checkTimeRunnable);
        }
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {}
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }
        TimeAnnouncementReceiver.releaseWakeLock();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
