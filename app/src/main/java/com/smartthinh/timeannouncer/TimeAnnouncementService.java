package com.smartthinh.timeannouncer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

public class TimeAnnouncementService extends Service implements TextToSpeech.OnInitListener {

    private static final String CHANNEL_ID = "TimeAnnouncerChannel";
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

    // Announcement sound configuration
    private boolean isPlaySoundEnabled = false;
    private boolean isCustomSound = false;
    private String soundPath = "";

    // App language ("en" or "vi")
    private String appLanguage = "en";

    private MediaPlayer currentPlayer;
    private Handler handler;
    private Runnable checkTimeRunnable;
    private int lastAnnouncedMinute = -1;
    private int lastAnnouncedHour = -1;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        String lang = prefs.getString("app_language", "en");
        super.attachBaseContext(MainActivity.updateLocaleContext(newBase, lang));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        loadSettings();
        initTts();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimeAnnouncer::WakeLock");
            wakeLock.acquire(24 * 60 * 60 * 1000L);
        }

        createNotificationChannel();
        Notification notification = buildForegroundNotification();
        startForeground(1, notification);

        handler = new Handler(Looper.getMainLooper());
        checkTimeRunnable = new Runnable() {
            @Override
            public void run() {
                checkAndAnnounce();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(checkTimeRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        loadSettings();
        applyTtsSettings();
        return START_STICKY;
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
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

        appLanguage = prefs.getString("app_language", "en");
    }

    private void initTts() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        isTtsReady = false;

        if (enginePackage != null && !enginePackage.isEmpty()) {
            tts = new TextToSpeech(this, this, enginePackage);
        } else {
            tts = new TextToSpeech(this, this);
        }
    }

    private Locale getTargetLocale() {
        return new Locale(appLanguage);
    }

    private void applyTtsSettings() {
        if (!isTtsReady || tts == null) return;

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
    }

    private void checkAndAnnounce() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        if (second == 0 && (minute != lastAnnouncedMinute || hour != lastAnnouncedHour)) {
            if (hours[hour]) {
                if (minute % interval == 0) {
                    lastAnnouncedMinute = minute;
                    lastAnnouncedHour = hour;
                    performAnnouncement(hour, minute);
                }
            }
        }
    }

    private void performAnnouncement(int hour, int minute) {
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

    private void speakTimeTts(int hour, int minute) {
        if (!isTtsReady || tts == null) return;

        applyTtsSettings();

        String speech;
        if ("vi".equalsIgnoreCase(appLanguage)) {
            if (minute == 0) {
                speech = getString(R.string.speech_exact_hour, hour);
            } else {
                speech = getString(R.string.speech_hour_minute, hour, minute);
            }
        } else {
            if (minute == 0) {
                speech = getString(R.string.speech_exact_hour, hour);
            } else {
                speech = getString(R.string.speech_hour_minute, hour, minute);
            }
        }

        int streamType = MainActivity.getStreamTypeFromIndex(audioStreamIndex);
        float vol = Math.max(0.0f, Math.min(1.0f, speechVolume / 100.0f));

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
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(getTargetLocale());
            isTtsReady = true;
            applyTtsSettings();
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
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCurrentPlayer();
        if (handler != null && checkTimeRunnable != null) {
            handler.removeCallbacks(checkTimeRunnable);
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
