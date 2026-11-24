package com.smartthinh.timeannouncer;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

public class AlarmAlertActivity extends Activity {

    private static final String TAG = "AlarmAlertActivity";
    private static final long AUTO_SILENCE_TIMEOUT_MS = 10 * 60 * 1000L; // 10 minutes auto-silence
    private static final long TTS_REPEAT_DELAY_MS = 1000L; // 1000ms delay between readings

    private TextView txtAlarmTime;
    private TextView txtAlarmMessage;
    private TextView txtSnoozeInfo;
    private Button btnSnooze;
    private Button btnDismiss;

    private MediaPlayer mediaPlayer;
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private Vibrator vibrator;

    private AlarmItem alarm;
    private long alarmId;
    private Handler handler;
    private Runnable autoSilenceRunnable;
    private Runnable ttsRepeatRunnable;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        String lang = prefs.getString("app_language", "en");
        super.attachBaseContext(MainActivity.updateLocaleContext(newBase, lang));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn on screen and show over keyguard/lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        setContentView(R.layout.dialog_alarm_alert);
        setFinishOnTouchOutside(false);

        alarmId = getIntent().getLongExtra(AlarmManagerHelper.EXTRA_ALARM_ID, -1);
        alarm = AlarmManagerHelper.getAlarmById(this, alarmId);

        txtAlarmTime = findViewById(R.id.txtAlertTime);
        txtAlarmMessage = findViewById(R.id.txtAlertMessage);
        txtSnoozeInfo = findViewById(R.id.txtAlertSnoozeInfo);
        btnSnooze = findViewById(R.id.btnAlertSnooze);
        btnDismiss = findViewById(R.id.btnAlertDismiss);

        if (alarm != null) {
            txtAlarmTime.setText(alarm.getFormattedTime());
            if (!alarm.getMessage().isEmpty()) {
                txtAlarmMessage.setVisibility(View.VISIBLE);
                txtAlarmMessage.setText(alarm.getMessage());
            } else {
                txtAlarmMessage.setVisibility(View.GONE);
            }

            int rem = alarm.getRemainingSnoozeCount();
            if (rem > 0) {
                txtSnoozeInfo.setText(getString(R.string.alarm_snooze_remaining, rem, alarm.getSnoozeMinutes()));
                txtSnoozeInfo.setVisibility(View.VISIBLE);
            } else {
                txtSnoozeInfo.setText(getString(R.string.alarm_snooze_none_left));
                txtSnoozeInfo.setVisibility(View.VISIBLE);
            }
        } else {
            txtAlarmTime.setText(getString(R.string.alarm_alert_title));
            txtAlarmMessage.setVisibility(View.GONE);
            txtSnoozeInfo.setVisibility(View.GONE);
        }

        btnSnooze.setOnClickListener(v -> handleSnooze());
        btnDismiss.setOnClickListener(v -> handleDismiss());

        handler = new Handler(Looper.getMainLooper());

        startVibration();
        startRingtone();
        initTtsAndSpeak();

        // Auto-silence timer after 10 minutes
        autoSilenceRunnable = () -> {
            Toast.makeText(AlarmAlertActivity.this, getString(R.string.alarm_auto_silenced), Toast.LENGTH_SHORT).show();
            handleDismiss();
        };
        handler.postDelayed(autoSilenceRunnable, AUTO_SILENCE_TIMEOUT_MS);
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
                // Fallback to internal bundled sound
                try {
                    AssetFileDescriptor afd = getAssets().openFd("sounds/bell.wav");
                    mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                    sourceSet = true;
                } catch (Exception ignored) {}
            }

            if (sourceSet) {
                // Looping continuously until user acts
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

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
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
                    public void onStart(String utteranceId) {
                    }

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
        clearNotification();

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
                // No repeats left
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
        finish();
    }

    private void handleDismiss() {
        cleanup();
        clearNotification();

        if (alarm != null) {
            // Reset snooze count for next time
            alarm.setRemainingSnoozeCount(alarm.getSnoozeCount());
            if (alarm.hasAnyDay()) {
                // Repeating alarm: schedule next occurrence
                AlarmManagerHelper.scheduleAlarm(this, alarm);
            } else {
                // One-time alarm: turn off
                alarm.setEnabled(false);
            }
            AlarmManagerHelper.saveOrUpdateAlarm(this, alarm);
            Toast.makeText(this, getString(R.string.toast_alarm_dismissed), Toast.LENGTH_SHORT).show();
        }
        finish();
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
    }

    private void clearNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel((int) (alarmId % Integer.MAX_VALUE));
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Prevent accidental back press dismissing without action, or snooze on back
        handleSnooze();
    }
}
