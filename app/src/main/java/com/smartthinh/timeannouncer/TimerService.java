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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TimerService extends Service implements TextToSpeech.OnInitListener {

    private static final String TAG = "TimerService";
    private static final String CHANNEL_ID_TIMER = "TimerChannel";
    private static final int NOTIF_ID_TIMER = 1002;

    public static final String ACTION_START_TIMER = "com.smartthinh.timeannouncer.ACTION_START_TIMER";
    public static final String ACTION_PAUSE_TIMER = "com.smartthinh.timeannouncer.ACTION_PAUSE_TIMER";
    public static final String ACTION_RESUME_TIMER = "com.smartthinh.timeannouncer.ACTION_RESUME_TIMER";
    public static final String ACTION_STOP_TIMER = "com.smartthinh.timeannouncer.ACTION_STOP_TIMER";
    public static final String ACTION_TIMER_RING = "com.smartthinh.timeannouncer.ACTION_TIMER_RING";
    public static final String ACTION_DISMISS_ALERT = "com.smartthinh.timeannouncer.ACTION_DISMISS_ALERT";

    public static final String EXTRA_TIMER_ID = "timer_id";

    // Broadcast action for UI updates
    public static final String ACTION_TIMER_TICK = "com.smartthinh.timeannouncer.ACTION_TIMER_TICK";

    // Auto-silence timer alert after 60 seconds (1 minute), much shorter than alarm's 10 minutes
    private static final long TIMER_AUTO_SILENCE_TIMEOUT_MS = 60 * 1000L;

    private TimerItem activeTimer;
    private long activeTimerId = -1;

    private Handler timerHandler;
    private Runnable countdownRunnable;

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private boolean isRinging = false;
    private Handler ttsRepeatHandler;
    private Runnable ttsRepeatRunnable;

    private Handler autoSilenceHandler;
    private Runnable autoSilenceRunnable;

    public static void startTimer(Context context, long timerId) {
        Intent intent = new Intent(context, TimerService.class);
        intent.setAction(ACTION_START_TIMER);
        intent.putExtra(EXTRA_TIMER_ID, timerId);
        startServiceInternal(context, intent);
    }

    public static void pauseTimer(Context context, long timerId) {
        Intent intent = new Intent(context, TimerService.class);
        intent.setAction(ACTION_PAUSE_TIMER);
        intent.putExtra(EXTRA_TIMER_ID, timerId);
        startServiceInternal(context, intent);
    }

    public static void resumeTimer(Context context, long timerId) {
        Intent intent = new Intent(context, TimerService.class);
        intent.setAction(ACTION_RESUME_TIMER);
        intent.putExtra(EXTRA_TIMER_ID, timerId);
        startServiceInternal(context, intent);
    }

    public static void stopTimer(Context context, long timerId) {
        Intent intent = new Intent(context, TimerService.class);
        intent.setAction(ACTION_STOP_TIMER);
        intent.putExtra(EXTRA_TIMER_ID, timerId);
        startServiceInternal(context, intent);
    }

    public static void dismissAlert(Context context, long timerId) {
        Intent intent = new Intent(context, TimerService.class);
        intent.setAction(ACTION_DISMISS_ALERT);
        intent.putExtra(EXTRA_TIMER_ID, timerId);
        startServiceInternal(context, intent);
    }

    public static void onTimerAlarmTriggered(Context context, long timerId) {
        Intent intent = new Intent(context, TimerService.class);
        intent.setAction(ACTION_TIMER_RING);
        intent.putExtra(EXTRA_TIMER_ID, timerId);
        startServiceInternal(context, intent);
    }

    private static void startServiceInternal(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start TimerService", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        timerHandler = new Handler(Looper.getMainLooper());
        ttsRepeatHandler = new Handler(Looper.getMainLooper());
        initTts();

        Notification notif = buildNotification("Time Announcer", "Bộ hẹn giờ đang sẵn sàng");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID_TIMER, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID_TIMER, notif);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            long timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1);
            String action = intent.getAction();

            switch (action) {
                case ACTION_START_TIMER:
                    handleStartTimer(timerId);
                    break;
                case ACTION_PAUSE_TIMER:
                    handlePauseTimer(timerId);
                    break;
                case ACTION_RESUME_TIMER:
                    handleResumeTimer(timerId);
                    break;
                case ACTION_STOP_TIMER:
                    handleStopTimer(timerId);
                    break;
                case ACTION_TIMER_RING:
                    handleTimerRing(timerId);
                    break;
                case ACTION_DISMISS_ALERT:
                    handleDismissAlert(timerId);
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    private void handleStartTimer(long timerId) {
        stopRinging();
        activeTimer = TimerManagerHelper.getTimerById(this, timerId);
        if (activeTimer == null) return;

        activeTimerId = timerId;
        long remaining = activeTimer.getMinutes() * 60 * 1000L;
        activeTimer.setRemainingMillis(remaining);
        activeTimer.setEndTimestamp(System.currentTimeMillis() + remaining);
        activeTimer.setState(TimerItem.STATE_RUNNING);
        TimerManagerHelper.saveOrUpdateTimer(this, activeTimer);

        scheduleAlarmManagerExact(activeTimer.getEndTimestamp());
        startCountdownLoop();
        updateNotification();
    }

    private void handlePauseTimer(long timerId) {
        if (activeTimer == null || activeTimer.getId() != timerId) {
            activeTimer = TimerManagerHelper.getTimerById(this, timerId);
        }
        if (activeTimer == null) return;

        cancelAlarmManagerExact();
        stopCountdownLoop();

        long remaining = activeTimer.getRemainingMillis();
        activeTimer.setRemainingMillis(remaining);
        activeTimer.setState(TimerItem.STATE_PAUSED);
        TimerManagerHelper.saveOrUpdateTimer(this, activeTimer);

        updateNotification();
        broadcastTick();
    }

    private void handleResumeTimer(long timerId) {
        if (activeTimer == null || activeTimer.getId() != timerId) {
            activeTimer = TimerManagerHelper.getTimerById(this, timerId);
        }
        if (activeTimer == null) return;

        long remaining = activeTimer.getRemainingMillis();
        if (remaining <= 0) {
            remaining = activeTimer.getMinutes() * 60 * 1000L;
        }
        activeTimer.setRemainingMillis(remaining);
        activeTimer.setEndTimestamp(System.currentTimeMillis() + remaining);
        activeTimer.setState(TimerItem.STATE_RUNNING);
        TimerManagerHelper.saveOrUpdateTimer(this, activeTimer);

        scheduleAlarmManagerExact(activeTimer.getEndTimestamp());
        startCountdownLoop();
        updateNotification();
        broadcastTick();
    }

    private void handleStopTimer(long timerId) {
        cancelAlarmManagerExact();
        stopCountdownLoop();
        stopRinging();

        TimerItem item = TimerManagerHelper.getTimerById(this, timerId);
        if (item != null) {
            item.setState(TimerItem.STATE_IDLE);
            item.setRemainingMillis(item.getMinutes() * 60 * 1000L);
            TimerManagerHelper.saveOrUpdateTimer(this, item);
        }
        if (activeTimer != null && activeTimer.getId() == timerId) {
            activeTimer = item;
        }

        broadcastTick();
        stopForeground(true);
        stopSelf();
    }

    private void handleTimerRing(long timerId) {
        cancelAlarmManagerExact();
        stopCountdownLoop();

        activeTimer = TimerManagerHelper.getTimerById(this, timerId);
        if (activeTimer == null) return;

        activeTimerId = timerId;
        activeTimer.setState(TimerItem.STATE_FINISHED);
        activeTimer.setRemainingMillis(0);
        TimerManagerHelper.saveOrUpdateTimer(this, activeTimer);

        startRinging();
        broadcastTick();

        // Launch alert activity
        try {
            Intent alertIntent = new Intent(this, TimerAlertActivity.class);
            alertIntent.putExtra(EXTRA_TIMER_ID, timerId);
            alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(alertIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch TimerAlertActivity", e);
        }
    }

    private void handleDismissAlert(long timerId) {
        stopRinging();
        TimerAlertActivity.closeActivity();

        TimerItem item = TimerManagerHelper.getTimerById(this, timerId);
        if (item != null) {
            item.setState(TimerItem.STATE_IDLE);
            item.setRemainingMillis(item.getMinutes() * 60 * 1000L);
            TimerManagerHelper.saveOrUpdateTimer(this, item);
        }
        if (activeTimer != null && activeTimer.getId() == timerId) {
            activeTimer = item;
        }

        broadcastTick();
        stopForeground(true);
        stopSelf();
    }

    private void startCountdownLoop() {
        stopCountdownLoop();
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (activeTimer != null && activeTimer.getState() == TimerItem.STATE_RUNNING) {
                    long remaining = activeTimer.getRemainingMillis();
                    if (remaining <= 0) {
                        handleTimerRing(activeTimer.getId());
                        return;
                    }
                    updateNotification();
                    broadcastTick();
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.post(countdownRunnable);
    }

    private void stopCountdownLoop() {
        if (timerHandler != null && countdownRunnable != null) {
            timerHandler.removeCallbacks(countdownRunnable);
        }
    }

    private void broadcastTick() {
        Intent tick = new Intent(ACTION_TIMER_TICK);
        if (activeTimer != null) {
            tick.putExtra(EXTRA_TIMER_ID, activeTimer.getId());
        }
        sendBroadcast(tick);
    }

    private void scheduleAlarmManagerExact(long triggerTime) {
        cancelAlarmManagerExact();
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(this, TimerReceiver.class);
        intent.setAction(TimerReceiver.ACTION_TIMER_TRIGGER);
        intent.putExtra(TimerReceiver.EXTRA_TIMER_ID, activeTimerId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(this, (int) (activeTimerId % Integer.MAX_VALUE), intent, flags);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerTime, pi);
                am.setAlarmClock(info, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule exact alarm for timer", e);
        }
    }

    private void cancelAlarmManagerExact() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(this, TimerReceiver.class);
        intent.setAction(TimerReceiver.ACTION_TIMER_TRIGGER);
        intent.putExtra(TimerReceiver.EXTRA_TIMER_ID, activeTimerId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(this, (int) (activeTimerId % Integer.MAX_VALUE), intent, flags);
        try {
            am.cancel(pi);
        } catch (Exception ignored) {}
    }

    private void startRinging() {
        isRinging = true;

        // Auto-silence timer after 60 seconds (1 minute - shorter than alarm's 10 minutes)
        stopAutoSilence();
        autoSilenceHandler = new Handler(Looper.getMainLooper());
        autoSilenceRunnable = () -> {
            Log.d(TAG, "Timer auto-silenced after 60 seconds");
            handleDismissAlert(activeTimerId);
        };
        autoSilenceHandler.postDelayed(autoSilenceRunnable, TIMER_AUTO_SILENCE_TIMEOUT_MS);

        // 1. Play Sound (looping)
        if (activeTimer != null && activeTimer.isSoundEnabled()) {
            try {
                mediaPlayer = new MediaPlayer();
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                mediaPlayer.setAudioAttributes(attrs);
                mediaPlayer.setLooping(true);

                if (activeTimer.isCustomSound() && activeTimer.getSoundPath().startsWith("content://")) {
                    mediaPlayer.setDataSource(this, Uri.parse(activeTimer.getSoundPath()));
                } else {
                    AssetFileDescriptor afd = getAssets().openFd(activeTimer.getSoundPath());
                    mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                }
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (Exception e) {
                Log.e(TAG, "Error playing timer sound", e);
            }
        }

        // 2. Vibrate (continuous pattern)
        if (activeTimer != null && activeTimer.isVibrate()) {
            try {
                vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    long[] pattern = {0, 600, 400, 600, 800};
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build();
                        VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0); // loop from index 0
                        vibrator.vibrate(effect, audioAttributes);
                    } else {
                        vibrator.vibrate(pattern, 0);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error vibrating timer", e);
            }
        }

        // 3. TTS Announcement if message exists
        if (activeTimer != null && !activeTimer.getMessage().isEmpty()) {
            startTtsLoop(activeTimer.getMessage());
        }

        // 4. Update notification with Dismiss action
        updateNotificationForFinished();
    }

    private void stopAutoSilence() {
        if (autoSilenceHandler != null && autoSilenceRunnable != null) {
            autoSilenceHandler.removeCallbacks(autoSilenceRunnable);
            autoSilenceRunnable = null;
        }
    }

    private void stopRinging() {
        stopAutoSilence();
        isRinging = false;
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {}
            vibrator = null;
        }
        stopTtsLoop();
        TimerReceiver.releaseWakeLock();
    }

    private void initTts() {
        SharedPreferences prefs = MainActivity.getSafeSharedPreferences(this);
        String engine = prefs.getString("tts_engine", "");
        try {
            if (!engine.isEmpty()) {
                tts = new TextToSpeech(this, this, engine);
            } else {
                tts = new TextToSpeech(this, this);
            }
        } catch (Exception e) {
            tts = new TextToSpeech(this, this);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true;
            applyTtsSettings();
        }
    }

    private void applyTtsSettings() {
        if (tts == null || !isTtsReady) return;
        try {
            SharedPreferences prefs = MainActivity.getSafeSharedPreferences(this);
            String lang = prefs.getString("app_language", "en");
            Locale locale = "vi".equalsIgnoreCase(lang) ? new Locale("vi", "VN") : Locale.US;
            tts.setLanguage(locale);

            float rate = prefs.getFloat("speech_rate", 1.0f);
            float pitch = prefs.getFloat("speech_pitch", 1.0f);
            tts.setSpeechRate(rate);
            tts.setPitch(pitch);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes aab = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                tts.setAudioAttributes(aab);
            }
        } catch (Exception ignored) {}
    }

    private void startTtsLoop(final String message) {
        stopTtsLoop();
        ttsRepeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRinging || tts == null || !isTtsReady) return;
                applyTtsSettings();
                Bundle params = new Bundle();
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);
                tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, "TimerTTS");
                ttsRepeatHandler.postDelayed(this, 4000);
            }
        };
        ttsRepeatHandler.postDelayed(ttsRepeatRunnable, 1000);
    }

    private void stopTtsLoop() {
        if (ttsRepeatHandler != null && ttsRepeatRunnable != null) {
            ttsRepeatHandler.removeCallbacks(ttsRepeatRunnable);
        }
        if (tts != null) {
            try {
                tts.stop();
            } catch (Exception ignored) {}
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_TIMER,
                    "Bộ hẹn giờ",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Thông báo tiến trình và cảnh báo khi bộ hẹn giờ kết thúc");
            channel.enableVibration(false);
            channel.setSound(null, null);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String content) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openAppIntent.putExtra("open_timer_id", activeTimerId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 100, openAppIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID_TIMER);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pi)
                .setOngoing(true);

        if (activeTimer != null) {
            if (activeTimer.getState() == TimerItem.STATE_RUNNING) {
                Intent pauseIntent = new Intent(this, TimerService.class);
                pauseIntent.setAction(ACTION_PAUSE_TIMER);
                pauseIntent.putExtra(EXTRA_TIMER_ID, activeTimer.getId());
                PendingIntent pausePi = PendingIntent.getService(this, 201, pauseIntent, flags);
                builder.addAction(android.R.drawable.ic_media_pause, getString(R.string.notif_action_pause), pausePi);
            } else if (activeTimer.getState() == TimerItem.STATE_PAUSED) {
                Intent resumeIntent = new Intent(this, TimerService.class);
                resumeIntent.setAction(ACTION_RESUME_TIMER);
                resumeIntent.putExtra(EXTRA_TIMER_ID, activeTimer.getId());
                PendingIntent resumePi = PendingIntent.getService(this, 202, resumeIntent, flags);
                builder.addAction(android.R.drawable.ic_media_play, getString(R.string.notif_action_resume), resumePi);
            }

            Intent stopIntent = new Intent(this, TimerService.class);
            stopIntent.setAction(ACTION_STOP_TIMER);
            stopIntent.putExtra(EXTRA_TIMER_ID, activeTimer.getId());
            PendingIntent stopPi = PendingIntent.getService(this, 203, stopIntent, flags);
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_action_stop), stopPi);
        }

        return builder.build();
    }

    private void updateNotification() {
        if (activeTimer == null) return;
        String title = activeTimer.getName();
        String timeStr = activeTimer.getFormattedRemainingTime();
        String status = (activeTimer.getState() == TimerItem.STATE_PAUSED) ? " (Đã tạm dừng)" : "";
        Notification notif = buildNotification(title, "Thời gian còn lại: " + timeStr + status);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID_TIMER, notif);
    }

    private void updateNotificationForFinished() {
        if (activeTimer == null) return;
        Intent dismissIntent = new Intent(this, TimerService.class);
        dismissIntent.setAction(ACTION_DISMISS_ALERT);
        dismissIntent.putExtra(EXTRA_TIMER_ID, activeTimer.getId());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent dismissPi = PendingIntent.getService(this, 101, dismissIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID_TIMER);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Hẹn giờ hoàn thành: " + activeTimer.getName())
                .setContentText("Bấm để tắt chuông và rung")
                .setContentIntent(dismissPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tắt", dismissPi)
                .setOngoing(true);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID_TIMER, builder.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCountdownLoop();
        stopRinging();
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
