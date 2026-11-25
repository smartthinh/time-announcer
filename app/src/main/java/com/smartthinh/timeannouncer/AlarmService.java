package com.smartthinh.timeannouncer;

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
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

public class AlarmService extends Service {

    private static final String TAG = "AlarmService";

    public static final String ACTION_START_ALARM = "com.smartthinh.timeannouncer.ACTION_START_ALARM";
    public static final String ACTION_SNOOZE_ALARM = "com.smartthinh.timeannouncer.ACTION_SNOOZE_ALARM";
    public static final String ACTION_DISMISS_ALARM = "com.smartthinh.timeannouncer.ACTION_DISMISS_ALARM";

    public static final int NOTIFICATION_ID = 8888;
    private static final long AUTO_SILENCE_TIMEOUT_MS = 10 * 60 * 1000L; // 10 minutes auto-silence
    private static final long TTS_REPEAT_DELAY_MS = 1000L; // 1000ms delay between readings

    private static AlarmService sInstance;

    private MediaPlayer mediaPlayer;
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private Vibrator vibrator;

    private AlarmItem alarm;
    private long alarmId = -1;
    private Handler handler;
    private Runnable autoSilenceRunnable;
    private Runnable ttsRepeatRunnable;

    public static AlarmService getInstance() {
        return sInstance;
    }

    public static void startAlarm(Context context, long alarmId) {
        Intent intent = new Intent(context, AlarmService.class);
        intent.setAction(ACTION_START_ALARM);
        intent.putExtra(AlarmManagerHelper.EXTRA_ALARM_ID, alarmId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void snoozeAlarm(Context context, long alarmId) {
        Intent intent = new Intent(context, AlarmService.class);
        intent.setAction(ACTION_SNOOZE_ALARM);
        intent.putExtra(AlarmManagerHelper.EXTRA_ALARM_ID, alarmId);
        context.startService(intent);
    }

    public static void dismissAlarm(Context context, long alarmId) {
        Intent intent = new Intent(context, AlarmService.class);
        intent.setAction(ACTION_DISMISS_ALARM);
        intent.putExtra(AlarmManagerHelper.EXTRA_ALARM_ID, alarmId);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        long id = intent.getLongExtra(AlarmManagerHelper.EXTRA_ALARM_ID, -1);
        if (id != -1) {
            this.alarmId = id;
            this.alarm = AlarmManagerHelper.getAlarmById(this, id);
        }

        if (ACTION_START_ALARM.equals(action)) {
            handleStartAlarm();
        } else if (ACTION_SNOOZE_ALARM.equals(action)) {
            handleSnooze();
        } else if (ACTION_DISMISS_ALARM.equals(action)) {
            handleDismiss();
        }

        return START_NOT_STICKY;
    }

    private void handleStartAlarm() {
        // Ensure notification channel exists
        createNotificationChannel();

        // Build and display foreground notification
        Notification notification = buildAlarmNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // Start vibration, ringtone, and TTS
        startVibration();
        startRingtone();
        initTtsAndSpeak();

        // Auto-silence timer after 10 minutes
        if (autoSilenceRunnable != null) {
            handler.removeCallbacks(autoSilenceRunnable);
        }
        autoSilenceRunnable = () -> {
            Toast.makeText(AlarmService.this, getString(R.string.alarm_auto_silenced), Toast.LENGTH_SHORT).show();
            handleDismiss();
        };
        handler.postDelayed(autoSilenceRunnable, AUTO_SILENCE_TIMEOUT_MS);

        // Also launch AlarmAlertActivity to show the full screen alert over lock screen
        launchAlertActivity();
    }

    private void launchAlertActivity() {
        Intent alertIntent = new Intent(this, AlarmAlertActivity.class);
        alertIntent.putExtra(AlarmManagerHelper.EXTRA_ALARM_ID, alarmId);
        alertIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        );
        try {
            startActivity(alertIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch AlarmAlertActivity from AlarmService", e);
        }
    }

    private Notification buildAlarmNotification() {
        String title = getString(R.string.alarm_alert_title);
        String text = (alarm != null) ? alarm.getFormattedTime() : getString(R.string.app_name);
        if (alarm != null && !alarm.getMessage().isEmpty()) {
            text += " - " + alarm.getMessage();
        }

        Intent alertIntent = new Intent(this, AlarmAlertActivity.class);
        alertIntent.putExtra(AlarmManagerHelper.EXTRA_ALARM_ID, alarmId);
        alertIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        );

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this,
                (int) (alarmId % Integer.MAX_VALUE),
                alertIntent,
                piFlags
        );

        Intent snoozeIntent = new Intent(this, AlarmService.class);
        snoozeIntent.setAction(ACTION_SNOOZE_ALARM);
        snoozeIntent.putExtra(AlarmManagerHelper.EXTRA_ALARM_ID, alarmId);
        PendingIntent snoozePi = PendingIntent.getService(this, 1001, snoozeIntent, piFlags);

        Intent dismissIntent = new Intent(this, AlarmService.class);
        dismissIntent.setAction(ACTION_DISMISS_ALARM);
        dismissIntent.putExtra(AlarmManagerHelper.EXTRA_ALARM_ID, alarmId);
        PendingIntent dismissPi = PendingIntent.getService(this, 1002, dismissIntent, piFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, AlarmReceiver.ALARM_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(android.R.drawable.ic_media_pause, getString(R.string.alarm_btn_snooze), snoozePi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.alarm_btn_dismiss), dismissPi);

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(AlarmReceiver.ALARM_CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        AlarmReceiver.ALARM_CHANNEL_ID,
                        getString(R.string.alarm_channel_name),
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription(getString(R.string.alarm_channel_desc));
                channel.setBypassDnd(true);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.enableVibration(true);

                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build();
                channel.setSound(null, audioAttributes);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void startVibration() {
        try {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 600, 400, 600, 800};
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {}
    }

    private void stopVibration() {
        try {
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Exception ignored) {}
    }

    private void startRingtone() {
        try {
            mediaPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }

            boolean sourceSet = false;
            if (alarm != null && !alarm.getRingtoneUri().isEmpty()) {
                try {
                    mediaPlayer.setDataSource(this, Uri.parse(alarm.getRingtoneUri()));
                    sourceSet = true;
                } catch (Exception e) {
                    Log.w(TAG, "Could not play custom ringtone, fallback to default", e);
                }
            }

            if (!sourceSet) {
                Uri defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (defaultUri == null) {
                    defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }
                if (defaultUri != null) {
                    try {
                        mediaPlayer.setDataSource(this, defaultUri);
                        sourceSet = true;
                    } catch (Exception ignored) {}
                }
            }

            if (!sourceSet) {
                try {
                    AssetFileDescriptor afd = getAssets().openFd("sounds/bell.wav");
                    mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                    sourceSet = true;
                } catch (Exception ignored) {}
            }

            if (sourceSet) {
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting ringtone", e);
        }
    }

    private void stopRingtone() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) {}
    }

    private void initTtsAndSpeak() {
        if (alarm == null || alarm.getMessage().isEmpty()) return;

        SharedPreferences prefs = MainActivity.getSafeSharedPreferences(this);
        String engine = prefs.getString("tts_engine", "");

        ttsRepeatRunnable = () -> {
            if (isTtsReady && alarm != null && !alarm.getMessage().isEmpty()) {
                speakTtsMessage();
            }
        };

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true;
                applySavedTtsSettings(prefs);

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {}

                    @Override
                    public void onDone(String utteranceId) {
                        scheduleNextTtsSpeak();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        scheduleNextTtsSpeak();
                    }

                    @Override
                    public void onError(String utteranceId, int errorCode) {
                        scheduleNextTtsSpeak();
                    }
                });

                speakTtsMessage();
            }
        }, engine.isEmpty() ? null : engine);
    }

    private void scheduleNextTtsSpeak() {
        if (handler != null && isTtsReady) {
            handler.removeCallbacks(ttsRepeatRunnable);
            handler.postDelayed(ttsRepeatRunnable, TTS_REPEAT_DELAY_MS);
        }
    }

    private void applySavedTtsSettings(SharedPreferences prefs) {
        if (tts == null) return;
        try {
            float rate = prefs.getFloat("speech_rate", 1.0f);
            float pitch = prefs.getFloat("speech_pitch", 1.0f);
            tts.setSpeechRate(rate);
            tts.setPitch(pitch);

            String voiceName = prefs.getString("tts_voice_name", "");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !voiceName.isEmpty()) {
                Set<Voice> voices = tts.getVoices();
                if (voices != null) {
                    for (Voice v : voices) {
                        if (v.getName().equals(voiceName)) {
                            tts.setVoice(v);
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void speakTtsMessage() {
        if (tts == null || !isTtsReady || alarm == null || alarm.getMessage().isEmpty()) return;
        try {
            int result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle params = new Bundle();
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);
                result = tts.speak(alarm.getMessage(), TextToSpeech.QUEUE_FLUSH, params, "AlarmTTS");
            } else {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "AlarmTTS");
                result = tts.speak(alarm.getMessage(), TextToSpeech.QUEUE_FLUSH, params);
            }
            if (result != TextToSpeech.SUCCESS) {
                scheduleNextTtsSpeak();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error speaking TTS", e);
            scheduleNextTtsSpeak();
        }
    }

    private void stopTts() {
        isTtsReady = false;
        try {
            if (ttsRepeatRunnable != null && handler != null) {
                handler.removeCallbacks(ttsRepeatRunnable);
            }
            if (tts != null) {
                tts.stop();
                tts.shutdown();
                tts = null;
            }
        } catch (Exception ignored) {}
    }

    private void handleSnooze() {
        cleanup();

        if (alarm != null) {
            int rem = alarm.getRemainingSnoozeCount();
            if (rem > 0) {
                alarm.setRemainingSnoozeCount(rem - 1);
                long snoozeTarget = System.currentTimeMillis() + (alarm.getSnoozeMinutes() * 60 * 1000L);
                AlarmManagerHelper.scheduleSnooze(this, alarm, snoozeTarget);
                AlarmManagerHelper.saveOrUpdateAlarm(this, alarm);

                Toast.makeText(this,
                        getString(R.string.toast_alarm_snoozed, alarm.getSnoozeMinutes(), (rem - 1)),
                        Toast.LENGTH_LONG).show();
            } else {
                alarm.setRemainingSnoozeCount(alarm.getSnoozeCount());
                if (alarm.hasAnyDay()) {
                    AlarmManagerHelper.scheduleAlarm(this, alarm);
                } else {
                    alarm.setEnabled(false);
                }
                AlarmManagerHelper.saveOrUpdateAlarm(this, alarm);
                Toast.makeText(this, getString(R.string.toast_alarm_no_snooze_left), Toast.LENGTH_SHORT).show();
            }
        }

        // Close alert activity if open
        AlarmAlertActivity.closeActivity();
        stopSelf();
    }

    private void handleDismiss() {
        cleanup();

        if (alarm != null) {
            alarm.setRemainingSnoozeCount(alarm.getSnoozeCount());
            if (alarm.hasAnyDay()) {
                AlarmManagerHelper.scheduleAlarm(this, alarm);
            } else {
                alarm.setEnabled(false);
            }
            AlarmManagerHelper.saveOrUpdateAlarm(this, alarm);
            Toast.makeText(this, getString(R.string.toast_alarm_dismissed), Toast.LENGTH_SHORT).show();
        }

        // Close alert activity if open
        AlarmAlertActivity.closeActivity();
        stopSelf();
    }

    private void cleanup() {
        if (autoSilenceRunnable != null && handler != null) {
            handler.removeCallbacks(autoSilenceRunnable);
        }
        if (ttsRepeatRunnable != null && handler != null) {
            handler.removeCallbacks(ttsRepeatRunnable);
        }
        stopRingtone();
        stopVibration();
        stopTts();
        AlarmReceiver.releaseWakeLock();
    }

    @Override
    public void onDestroy() {
        cleanup();
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
