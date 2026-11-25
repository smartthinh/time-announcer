package com.smartthinh.timeannouncer;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class MainActivity extends Activity {

    public static final String PREFS_NAME = "TimeAnnouncerPrefs";
    private static final int REQUEST_CODE_PICK_AUDIO = 202;
    private static final int REQUEST_CODE_PICK_ALARM_AUDIO = 203;

    private CheckBox chkAutoSpeak;
    private Button btnInterval;
    private Button btnHours;
    private Button btnTtsSettings;

    // Checkbox and button for announcement sound
    private CheckBox chkPlaySound;
    private Button btnSelectSound;

    // Alarm button
    private Button btnAlarmList;
    private AlarmItem currentEditingAlarm;
    private Button currentAlarmRingtoneBtnRef;
    private MediaPlayer alarmPreviewPlayer;

    // Update button and download thread
    private Button btnCheckUpdate;
    private Thread downloadThread;
    private volatile boolean isDownloadCancelled = false;

    // Language and About buttons
    private Button btnLanguage;
    private Button btnAbout;

    private int selectedInterval = 60; // Default 60 minutes
    private boolean[] selectedHours = new boolean[24]; // 24 hours (0 -> 23)

    private final int[] intervals = {5, 10, 15, 20, 30, 60};

    private int selectedStreamIndex = 0; // Default: Media stream

    // TTS Configuration
    private String selectedEngine = "";
    private String selectedVoiceName = "";
    private float speechRate = 1.0f;
    private float speechPitch = 1.0f;
    private int speechVolume = 50; // Default 50%

    // Announcement Sound Configuration
    private boolean isPlaySoundEnabled = false;
    private boolean isCustomSound = false;
    private String selectedSoundPath = "sounds/bell.wav";
    private String selectedSoundName = "bell";

    // Current app language ("en" or "vi")
    private String currentLanguage = "en";

    private TextToSpeech previewTts;
    private boolean isPreviewTtsReady = false;
    private MediaPlayer previewPlayer;

    public static Context updateLocaleContext(Context context, String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            return context;
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lang = prefs.getString("app_language", null);
        if (lang == null) {
            // First time opening the app: check system language
            Locale sysLocale;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sysLocale = Resources.getSystem().getConfiguration().getLocales().get(0);
            } else {
                sysLocale = Resources.getSystem().getConfiguration().locale;
            }
            String sysLang = (sysLocale != null) ? sysLocale.getLanguage() : "en";
            if ("vi".equalsIgnoreCase(sysLang)) {
                lang = "vi";
            } else {
                lang = "en"; // English or other languages default to English
            }
            prefs.edit().putString("app_language", lang).apply();
        }
        super.attachBaseContext(updateLocaleContext(newBase, lang));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply saved locale to resources
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentLanguage = prefs.getString("app_language", "en");
        Locale locale = new Locale(currentLanguage);
        Locale.setDefault(locale);
        Configuration config = getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());

        setContentView(R.layout.activity_main);

        chkAutoSpeak = findViewById(R.id.chkAutoSpeak);
        btnInterval = findViewById(R.id.btnInterval);
        btnHours = findViewById(R.id.btnHours);
        btnTtsSettings = findViewById(R.id.btnTtsSettings);
        chkPlaySound = findViewById(R.id.chkPlaySound);
        btnSelectSound = findViewById(R.id.btnSelectSound);
        btnAlarmList = findViewById(R.id.btnAlarmList);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        btnLanguage = findViewById(R.id.btnLanguage);
        btnAbout = findViewById(R.id.btnAbout);

        for (int i = 0; i < 24; i++) {
            selectedHours[i] = true;
        }

        loadPreferences();
        initPreviewTts(selectedEngine, null);
        updateUIState(chkAutoSpeak.isChecked());
        updateAlarmListButtonVisibility();
        checkAndRequestInitialPermissions();

        // Checkbox "Auto speak time"
        chkAutoSpeak.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateUIState(isChecked);

            if (isChecked) {
                requestNotificationPermission();
                requestBatteryOptimizationExemption();
                startAnnouncerService();
                Toast.makeText(this, getString(R.string.toast_auto_speak_enabled), Toast.LENGTH_SHORT).show();
            } else {
                stopAnnouncerService();
                Toast.makeText(this, getString(R.string.toast_auto_speak_disabled), Toast.LENGTH_SHORT).show();
            }
            savePreferences();
        });

        // Button "Announcement interval"
        btnInterval.setOnClickListener(v -> showIntervalDialog());

        // Button "Hours to announce"
        btnHours.setOnClickListener(v -> showHoursDialog());

        // Button "TTS engine settings"
        btnTtsSettings.setOnClickListener(v -> showTtsSettingsDialog());

        // Checkbox "Play sound with announcement"
        chkPlaySound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isPlaySoundEnabled = isChecked;
            btnSelectSound.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            savePreferences();
            restartServiceIfRunning();
        });

        // Button "Select sound"
        btnSelectSound.setOnClickListener(v -> showSoundSelectionDialog());

        // Button "Set alarm"
        btnAlarmList.setOnClickListener(v -> {
            checkExactAlarmPermission();
            showAlarmListDialog();
        });

        // Button "Check for updates"
        btnCheckUpdate.setOnClickListener(v -> checkForUpdates(true));

        // Automatically check for updates in background on app launch
        checkForUpdates(false);

        // Button "Language" (above About button)
        btnLanguage.setOnClickListener(v -> showLanguageDialog());

        // Button "About"
        btnAbout.setOnClickListener(v -> showAboutDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAlarmListButtonVisibility();
    }

    private void updateUIState(boolean isEnabled) {
        int mainVisibility = isEnabled ? View.VISIBLE : View.GONE;
        btnInterval.setVisibility(mainVisibility);
        btnHours.setVisibility(mainVisibility);
        btnTtsSettings.setVisibility(mainVisibility);
        chkPlaySound.setVisibility(mainVisibility);

        btnInterval.setText(getString(R.string.btn_interval_prefix, selectedInterval));
        btnHours.setText(getString(R.string.btn_hours_prefix, countSelectedHours()));

        if (isEnabled && isPlaySoundEnabled) {
            btnSelectSound.setVisibility(View.VISIBLE);
        } else {
            btnSelectSound.setVisibility(View.GONE);
        }
        btnSelectSound.setText(getString(R.string.btn_select_sound_prefix, selectedSoundName));
    }

    private int countSelectedHours() {
        int count = 0;
        for (boolean b : selectedHours) {
            if (b) count++;
        }
        return count;
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(fallback);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void checkAndRequestInitialPermissions() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean requested = prefs.getBoolean("initial_permissions_requested", false);
        if (!requested) {
            prefs.edit().putBoolean("initial_permissions_requested", true).apply();
            requestRequiredPermissions();
        }
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                return;
            }
        }
        requestBatteryAndAlarmPermissions();
    }

    private void requestBatteryAndAlarmPermissions() {
        requestBatteryOptimizationExemption();
        checkExactAlarmPermission();
        checkFullScreenIntentPermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            requestBatteryAndAlarmPermissions();
        }
    }

    private void showIntervalDialog() {
        int currentIndex = 5;
        String[] intervalLabels = new String[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            if ("vi".equals(currentLanguage)) {
                intervalLabels[i] = intervals[i] + " phút";
            } else {
                intervalLabels[i] = intervals[i] + " minutes";
            }
            if (intervals[i] == selectedInterval) {
                currentIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_interval_title))
                .setSingleChoiceItems(intervalLabels, currentIndex, (dialog, which) -> {
                    selectedInterval = intervals[which];
                    btnInterval.setText(getString(R.string.btn_interval_prefix, selectedInterval));
                    savePreferences();
                    restartServiceIfRunning();
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void showHoursDialog() {
        String[] hoursArray = new String[24];
        for (int i = 0; i < 24; i++) {
            if ("vi".equals(currentLanguage)) {
                hoursArray[i] = String.format(Locale.US, "%02d:00 (%d giờ)", i, i);
            } else {
                hoursArray[i] = String.format(Locale.US, "%02d:00 (%d h)", i, i);
            }
        }

        boolean[] tempSelected = selectedHours.clone();

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_hours_title))
                .setMultiChoiceItems(hoursArray, tempSelected, (dialog, which, isChecked) -> {
                    tempSelected[which] = isChecked;
                })
                .setPositiveButton(getString(R.string.btn_ok), (dialog, which) -> {
                    System.arraycopy(tempSelected, 0, selectedHours, 0, 24);
                    btnHours.setText(getString(R.string.btn_hours_prefix, countSelectedHours()));
                    savePreferences();
                    restartServiceIfRunning();
                    Toast.makeText(this, getString(R.string.toast_hours_saved), Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(getString(R.string.btn_select_all), (dialog, which) -> {
                    for (int i = 0; i < 24; i++) selectedHours[i] = true;
                    btnHours.setText(getString(R.string.btn_hours_prefix, 24));
                    savePreferences();
                    restartServiceIfRunning();
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private List<SoundItem> getBuiltInSounds() {
        List<SoundItem> list = new ArrayList<>();
        try {
            String[] files = getAssets().list("sounds");
            if (files != null) {
                for (String f : files) {
                    String nameWithoutExt = f;
                    int dotIndex = f.lastIndexOf('.');
                    if (dotIndex > 0) {
                        nameWithoutExt = f.substring(0, dotIndex);
                    }
                    list.add(new SoundItem(nameWithoutExt, "sounds/" + f, true));
                }
            }
        } catch (Exception ignored) {}
        Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return list;
    }

    private void showSoundSelectionDialog() {
        List<SoundItem> soundList = getBuiltInSounds();

        if (isCustomSound && !selectedSoundPath.isEmpty()) {
            soundList.add(0, new SoundItem(selectedSoundName + " (" + getString(R.string.btn_select_sound_prefix, "").replace(": ", "").trim() + ")", selectedSoundPath, false));
        }

        String[] soundNames = new String[soundList.size()];
        int selectedIndex = 0;

        for (int i = 0; i < soundList.size(); i++) {
            soundNames[i] = soundList.get(i).name;
            if (soundList.get(i).path.equals(selectedSoundPath)) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_sound_title))
                .setSingleChoiceItems(soundNames, selectedIndex, (dialog, which) -> {
                    SoundItem item = soundList.get(which);
                    selectedSoundPath = item.path;
                    selectedSoundName = item.name.split(" \\(")[0];
                    isCustomSound = !item.isAsset;
                    playPreviewSound(item);
                })
                .setPositiveButton(getString(R.string.btn_ok), (dialog, which) -> {
                    stopPreviewSound();
                    btnSelectSound.setText(getString(R.string.btn_select_sound_prefix, selectedSoundName));
                    savePreferences();
                    restartServiceIfRunning();
                })
                .setNeutralButton(getString(R.string.btn_select_storage), (dialog, which) -> {
                    stopPreviewSound();
                    openStorageFilePicker();
                })
                .setNegativeButton(getString(R.string.btn_cancel), (dialog, which) -> stopPreviewSound())
                .setOnDismissListener(dialog -> stopPreviewSound())
                .show();
    }

    private void openStorageFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            startActivityForResult(intent, REQUEST_CODE_PICK_AUDIO);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.setType("audio/*");
                startActivityForResult(fallback, REQUEST_CODE_PICK_AUDIO);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_AUDIO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                } catch (Exception ignored) {}

                String fileName = getFileNameFromUri(uri);
                selectedSoundPath = uri.toString();
                selectedSoundName = fileName;
                isCustomSound = true;

                btnSelectSound.setText(getString(R.string.btn_select_sound_prefix, selectedSoundName));
                savePreferences();
                restartServiceIfRunning();

                playPreviewSound(new SoundItem(selectedSoundName, selectedSoundPath, false));
            }
        } else if (requestCode == REQUEST_CODE_PICK_ALARM_AUDIO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                } catch (Exception ignored) {}

                String fileName = getFileNameFromUri(uri);
                if (fileName == null || fileName.isEmpty()) {
                    fileName = "Audio";
                }
                if (currentEditingAlarm != null) {
                    currentEditingAlarm.setRingtoneUri(uri.toString());
                    currentEditingAlarm.setRingtoneTitle(fileName);
                }
                if (currentAlarmRingtoneBtnRef != null) {
                    currentAlarmRingtoneBtnRef.setText(getString(R.string.alarm_btn_ringtone, fileName));
                }
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        if (result != null) {
            int lastDot = result.lastIndexOf('.');
            if (lastDot > 0) {
                result = result.substring(0, lastDot);
            }
        } else {
            result = "External audio";
        }
        return result;
    }

    private void playPreviewSound(SoundItem item) {
        stopPreviewSound();
        try {
            previewPlayer = new MediaPlayer();
            applyAudioAttributesToPlayer(previewPlayer, selectedStreamIndex);

            if (item.isAsset) {
                AssetFileDescriptor afd = getAssets().openFd(item.path);
                previewPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } else {
                previewPlayer.setDataSource(this, Uri.parse(item.path));
            }
            float vol = Math.max(0.0f, Math.min(1.0f, speechVolume / 100.0f));
            previewPlayer.setVolume(vol, vol);
            previewPlayer.prepare();
            previewPlayer.start();
        } catch (Exception ignored) {
            stopPreviewSound();
        }
    }

    private void stopPreviewSound() {
        if (previewPlayer != null) {
            try {
                previewPlayer.stop();
                previewPlayer.release();
            } catch (Exception ignored) {}
            previewPlayer = null;
        }
    }

    public static void applyAudioAttributesToPlayer(MediaPlayer mp, int streamIndex) {
        int streamType = getStreamTypeFromIndex(streamIndex);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
            mp.setAudioAttributes(aab.build());
        } else {
            mp.setAudioStreamType(streamType);
        }
    }

    private String getStreamName(int index) {
        switch (index) {
            case 1: return getString(R.string.stream_ring);
            case 2: return getString(R.string.stream_alarm);
            case 3: return getString(R.string.stream_accessibility);
            case 4: return getString(R.string.stream_notification);
            case 0:
            default:
                return getString(R.string.stream_media);
        }
    }

    private void showTtsSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_tts_title));

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_tts_settings, null);
        builder.setView(dialogView);

        Button btnSelectEngine = dialogView.findViewById(R.id.btnSelectEngine);
        Button btnSelectVoice = dialogView.findViewById(R.id.btnSelectVoice);
        TextView tvSpeedLabel = dialogView.findViewById(R.id.tvSpeedLabel);
        SeekBar sbSpeed = dialogView.findViewById(R.id.sbSpeed);
        TextView tvPitchLabel = dialogView.findViewById(R.id.tvPitchLabel);
        SeekBar sbPitch = dialogView.findViewById(R.id.sbPitch);
        TextView tvVolumeLabel = dialogView.findViewById(R.id.tvVolumeLabel);
        SeekBar sbVolume = dialogView.findViewById(R.id.sbVolume);
        Button btnAudioStream = dialogView.findViewById(R.id.btnAudioStream);
        Button btnTestVoice = dialogView.findViewById(R.id.btnTestVoice);

        updateEngineButtonText(btnSelectEngine);
        updateVoiceButtonText(btnSelectVoice);
        btnAudioStream.setText(getString(R.string.btn_select_stream_prefix, getStreamName(selectedStreamIndex)));

        btnSelectEngine.setOnClickListener(v -> showEngineSelectionDialog(btnSelectEngine, btnSelectVoice));
        btnSelectVoice.setOnClickListener(v -> showVoiceSelectionDialog(btnSelectVoice));

        int speedProgress = (int) Math.round((speechRate - 0.5f) * 10);
        sbSpeed.setMax(20);
        sbSpeed.setProgress(Math.max(0, Math.min(20, speedProgress)));
        tvSpeedLabel.setText(getString(R.string.label_speed_prefix, speechRate));
        sbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speechRate = 0.5f + (progress * 0.1f);
                tvSpeedLabel.setText(getString(R.string.label_speed_prefix, speechRate));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        int pitchProgress = (int) Math.round((speechPitch - 0.5f) * 10);
        sbPitch.setMax(20);
        sbPitch.setProgress(Math.max(0, Math.min(20, pitchProgress)));
        tvPitchLabel.setText(getString(R.string.label_pitch_prefix, speechPitch));
        sbPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speechPitch = 0.5f + (progress * 0.1f);
                tvPitchLabel.setText(getString(R.string.label_pitch_prefix, speechPitch));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbVolume.setMax(100);
        sbVolume.setProgress(speechVolume);
        tvVolumeLabel.setText(getString(R.string.label_volume_prefix, speechVolume));
        sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speechVolume = progress;
                tvVolumeLabel.setText(getString(R.string.label_volume_prefix, speechVolume));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnAudioStream.setOnClickListener(v -> showAudioStreamDialog(btnAudioStream));
        btnTestVoice.setOnClickListener(v -> speakSample(getString(R.string.sample_speech)));

        builder.setPositiveButton(getString(R.string.btn_save), (dialog, which) -> {
            savePreferences();
            restartServiceIfRunning();
            Toast.makeText(this, getString(R.string.toast_tts_saved), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(getString(R.string.btn_close), null);
        builder.show();
    }

    private void updateEngineButtonText(Button btn) {
        if (selectedEngine.isEmpty()) {
            btn.setText(getString(R.string.tts_engine_default));
        } else {
            btn.setText(getString(R.string.tts_engine_prefix, selectedEngine));
        }
    }

    private void updateVoiceButtonText(Button btn) {
        if (selectedVoiceName.isEmpty()) {
            btn.setText(getString(R.string.tts_voice_default));
        } else {
            btn.setText(getString(R.string.tts_voice_prefix, selectedVoiceName));
        }
    }

    private void showEngineSelectionDialog(Button btnEngine, Button btnVoice) {
        PackageManager pm = getPackageManager();
        Intent ttsIntent = new Intent("android.intent.action.TTS_SERVICE");
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(ttsIntent, PackageManager.MATCH_ALL);

        Map<String, String> engineMap = new HashMap<>();
        if (resolveInfos != null) {
            for (ResolveInfo ri : resolveInfos) {
                if (ri.serviceInfo != null) {
                    String pkg = ri.serviceInfo.packageName;
                    String label = ri.loadLabel(pm).toString();
                    engineMap.put(pkg, label);
                }
            }
        }

        if (previewTts != null) {
            List<TextToSpeech.EngineInfo> sdkEngines = previewTts.getEngines();
            if (sdkEngines != null) {
                for (TextToSpeech.EngineInfo ei : sdkEngines) {
                    if (!engineMap.containsKey(ei.name)) {
                        engineMap.put(ei.name, ei.label);
                    }
                }
            }
        }

        if (engineMap.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_engines), Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> pkgList = new ArrayList<>(engineMap.keySet());
        Collections.sort(pkgList);

        String[] displayItems = new String[pkgList.size()];
        int selectedIndex = 0;
        for (int i = 0; i < pkgList.size(); i++) {
            String pkg = pkgList.get(i);
            String label = engineMap.get(pkg);
            displayItems[i] = label + " (" + pkg + ")";
            if (pkg.equals(selectedEngine)) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_engine_title))
                .setSingleChoiceItems(displayItems, selectedIndex, (dialog, which) -> {
                    selectedEngine = pkgList.get(which);
                    selectedVoiceName = "";
                    updateEngineButtonText(btnEngine);
                    updateVoiceButtonText(btnVoice);
                    initPreviewTts(selectedEngine, () -> updateVoiceButtonText(btnVoice));
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private Locale getCurrentSystemLocale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return getResources().getConfiguration().getLocales().get(0);
        } else {
            return getResources().getConfiguration().locale;
        }
    }

    private void showVoiceSelectionDialog(Button btnVoice) {
        if (previewTts == null || !isPreviewTtsReady) {
            Toast.makeText(this, getString(R.string.toast_tts_loading), Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Toast.makeText(this, getString(R.string.toast_requires_lollipop), Toast.LENGTH_SHORT).show();
            return;
        }

        Locale systemLocale = getCurrentSystemLocale();
        String systemLang = systemLocale.getLanguage();

        Set<Voice> allVoices = previewTts.getVoices();
        if (allVoices == null || allVoices.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_custom_voices), Toast.LENGTH_SHORT).show();
            return;
        }

        List<Voice> matchedVoices = new ArrayList<>();
        for (Voice v : allVoices) {
            if (v.getLocale() != null && v.getLocale().getLanguage().equalsIgnoreCase(systemLang)) {
                matchedVoices.add(v);
            }
        }

        if (matchedVoices.isEmpty()) {
            String msg = getString(R.string.toast_no_voices_for_lang, systemLocale.getDisplayLanguage());
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }

        Collections.sort(matchedVoices, (v1, v2) -> v1.getName().compareToIgnoreCase(v2.getName()));

        String[] voiceItems = new String[matchedVoices.size() + 1];
        voiceItems[0] = getString(R.string.tts_voice_auto_label);
        int selectedIndex = 0;

        for (int i = 0; i < matchedVoices.size(); i++) {
            Voice v = matchedVoices.get(i);
            voiceItems[i + 1] = v.getName();
            if (v.getName().equals(selectedVoiceName)) {
                selectedIndex = i + 1;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_voice_title, systemLocale.getDisplayLanguage()))
                .setSingleChoiceItems(voiceItems, selectedIndex, (dialog, which) -> {
                    if (which == 0) {
                        selectedVoiceName = "";
                    } else {
                        selectedVoiceName = matchedVoices.get(which - 1).getName();
                    }
                    updateVoiceButtonText(btnVoice);
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void showAudioStreamDialog(Button btnAudioStream) {
        String[] streamNames = {
                getString(R.string.stream_media),
                getString(R.string.stream_ring),
                getString(R.string.stream_alarm),
                getString(R.string.stream_accessibility),
                getString(R.string.stream_notification)
        };

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_stream_title))
                .setSingleChoiceItems(streamNames, selectedStreamIndex, (dialog, which) -> {
                    selectedStreamIndex = which;
                    btnAudioStream.setText(getString(R.string.btn_select_stream_prefix, streamNames[selectedStreamIndex]));
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void speakSample(String text) {
        if (previewTts == null || !isPreviewTtsReady) {
            Toast.makeText(this, getString(R.string.toast_tts_loading), Toast.LENGTH_SHORT).show();
            return;
        }

        previewTts.setSpeechRate(speechRate);
        previewTts.setPitch(speechPitch);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!selectedVoiceName.isEmpty()) {
                Set<Voice> voices = previewTts.getVoices();
                if (voices != null) {
                    for (Voice v : voices) {
                        if (v.getName().equals(selectedVoiceName)) {
                            previewTts.setVoice(v);
                            break;
                        }
                    }
                }
            } else {
                previewTts.setLanguage(getCurrentSystemLocale());
            }

            int streamType = getStreamTypeFromIndex(selectedStreamIndex);
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
            previewTts.setAudioAttributes(aab.build());

            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, streamType);
            float vol = Math.max(0.0f, Math.min(1.0f, speechVolume / 100.0f));
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol);

            previewTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "PreviewSample");
        } else {
            previewTts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    public static int getStreamTypeFromIndex(int index) {
        switch (index) {
            case 1:
                return AudioManager.STREAM_RING;
            case 2:
                return AudioManager.STREAM_ALARM;
            case 3:
                return (Build.VERSION.SDK_INT >= 26) ? AudioManager.STREAM_ACCESSIBILITY : AudioManager.STREAM_MUSIC;
            case 4:
                return AudioManager.STREAM_NOTIFICATION;
            case 0:
            default:
                return AudioManager.STREAM_MUSIC;
        }
    }

    private void initPreviewTts(String enginePackage, Runnable onReady) {
        if (previewTts != null) {
            previewTts.stop();
            previewTts.shutdown();
        }
        isPreviewTtsReady = false;

        TextToSpeech.OnInitListener listener = status -> {
            if (status == TextToSpeech.SUCCESS) {
                previewTts.setLanguage(getCurrentSystemLocale());
                isPreviewTtsReady = true;
                if (onReady != null) {
                    runOnUiThread(onReady);
                }
            }
        };

        if (enginePackage != null && !enginePackage.isEmpty()) {
            previewTts = new TextToSpeech(this, listener, enginePackage);
        } else {
            previewTts = new TextToSpeech(this, listener);
        }
    }

    /**
     * Language Selection Dialog (English and Tiếng Việt)
     */
    private void showLanguageDialog() {
        String[] languages = {"English", "Tiếng Việt"};
        int selectedIndex = "vi".equalsIgnoreCase(currentLanguage) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_language_title))
                .setSingleChoiceItems(languages, selectedIndex, (dialog, which) -> {
                    String newLang = (which == 1) ? "vi" : "en";
                    dialog.dismiss();
                    if (!newLang.equals(currentLanguage)) {
                        currentLanguage = newLang;
                        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                        editor.putString("app_language", currentLanguage);
                        editor.apply();

                        // Recreate Activity to apply new language
                        recreate();
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_about_title));

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_about, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        Button btnDetailIntro = dialogView.findViewById(R.id.btnDetailIntro);
        Button btnGuide = dialogView.findViewById(R.id.btnGuide);
        Button btnLicense = dialogView.findViewById(R.id.btnLicense);
        Button btnSourceCode = dialogView.findViewById(R.id.btnSourceCode);
        Button btnCloseAbout = dialogView.findViewById(R.id.btnCloseAbout);

        btnDetailIntro.setOnClickListener(v -> showDetailIntroDialog());
        btnGuide.setOnClickListener(v -> showGuideDialog());
        btnLicense.setOnClickListener(v -> showLicenseDialog());

        // Updated GitHub repository link to time-announcer
        btnSourceCode.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/smartthinh/time-announcer"));
                startActivity(browserIntent);
            } catch (Exception ignored) {}
        });

        btnCloseAbout.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showDetailIntroDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_about_title))
                .setMessage(getString(R.string.about_detail_content))
                .setPositiveButton(getString(R.string.btn_ok), null)
                .show();
    }

    private void showGuideDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.btn_guide))
                .setMessage(getString(R.string.about_guide_content))
                .setPositiveButton(getString(R.string.btn_ok), null)
                .show();
    }

    private void showLicenseDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.btn_license))
                .setMessage(getString(R.string.about_license_content))
                .setPositiveButton(getString(R.string.btn_ok), null)
                .show();
    }

    private void startAnnouncerService() {
        Intent intent = new Intent(this, TimeAnnouncementService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopAnnouncerService() {
        Intent intent = new Intent(this, TimeAnnouncementService.class);
        stopService(intent);
    }

    private void restartServiceIfRunning() {
        if (chkAutoSpeak.isChecked()) {
            startAnnouncerService();
        }
    }

    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("auto_speak", chkAutoSpeak.isChecked());
        editor.putInt("interval", selectedInterval);
        for (int i = 0; i < 24; i++) {
            editor.putBoolean("hour_" + i, selectedHours[i]);
        }
        editor.putString("tts_engine", selectedEngine);
        editor.putString("tts_voice", selectedVoiceName);
        editor.putFloat("speech_rate", speechRate);
        editor.putFloat("speech_pitch", speechPitch);
        editor.putInt("speech_volume", speechVolume);
        editor.putInt("audio_stream_index", selectedStreamIndex);

        editor.putBoolean("play_sound", isPlaySoundEnabled);
        editor.putBoolean("sound_is_custom", isCustomSound);
        editor.putString("sound_path", selectedSoundPath);
        editor.putString("sound_name", selectedSoundName);

        editor.putString("app_language", currentLanguage);

        editor.apply();
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        chkAutoSpeak.setChecked(prefs.getBoolean("auto_speak", false));
        selectedInterval = prefs.getInt("interval", 60);
        for (int i = 0; i < 24; i++) {
            selectedHours[i] = prefs.getBoolean("hour_" + i, true);
        }
        selectedEngine = prefs.getString("tts_engine", "");
        selectedVoiceName = prefs.getString("tts_voice", "");
        speechRate = prefs.getFloat("speech_rate", 1.0f);
        speechPitch = prefs.getFloat("speech_pitch", 1.0f);
        speechVolume = prefs.getInt("speech_volume", 50);
        selectedStreamIndex = prefs.getInt("audio_stream_index", 0);

        isPlaySoundEnabled = prefs.getBoolean("play_sound", false);
        chkPlaySound.setChecked(isPlaySoundEnabled);
        isCustomSound = prefs.getBoolean("sound_is_custom", false);
        selectedSoundPath = prefs.getString("sound_path", "sounds/bell.wav");
        selectedSoundName = prefs.getString("sound_name", "bell");

        currentLanguage = prefs.getString("app_language", "en");
    }

    private void checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        }
        checkFullScreenIntentPermission();
    }

    private void checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        }
    }

    private void updateAlarmListButtonVisibility() {
        if (btnAlarmList != null) {
            btnAlarmList.setVisibility(View.VISIBLE);
        }
    }

    private void showAlarmEditDialog(final AlarmItem alarmToEdit, final boolean returnToList) {
        final boolean isEditMode = (alarmToEdit != null);
        final AlarmItem currentAlarm = isEditMode ? alarmToEdit : new AlarmItem();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_alarm_edit, null);
        final EditText edtHour = dialogView.findViewById(R.id.edtAlarmHour);
        final EditText edtMinute = dialogView.findViewById(R.id.edtAlarmMinute);
        final EditText edtSnoozeCount = dialogView.findViewById(R.id.edtSnoozeCount);
        final EditText edtSnoozeInterval = dialogView.findViewById(R.id.edtSnoozeInterval);
        final EditText edtMessage = dialogView.findViewById(R.id.edtAlarmMessage);
        final Button btnDays = dialogView.findViewById(R.id.btnAlarmDays);
        final Button btnRingtone = dialogView.findViewById(R.id.btnAlarmRingtone);

        // Auto-fill current time or existing time
        if (isEditMode) {
            edtHour.setText(String.format(Locale.US, "%02d", currentAlarm.getHour()));
            edtMinute.setText(String.format(Locale.US, "%02d", currentAlarm.getMinute()));
        } else {
            Calendar now = Calendar.getInstance();
            edtHour.setText(String.format(Locale.US, "%02d", now.get(Calendar.HOUR_OF_DAY)));
            edtMinute.setText(String.format(Locale.US, "%02d", now.get(Calendar.MINUTE)));
        }

        // Limit hour 0-23: if entered > 23, clear input
        edtHour.addTextChangedListener(new TextWatcher() {
            private boolean isEditing = false;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isEditing) return;
                if (s.length() > 0) {
                    try {
                        int val = Integer.parseInt(s.toString());
                        if (val > 23) {
                            isEditing = true;
                            s.clear();
                            isEditing = false;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        // Limit minute 0-59: if entered > 59, clear input
        edtMinute.addTextChangedListener(new TextWatcher() {
            private boolean isEditing = false;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isEditing) return;
                if (s.length() > 0) {
                    try {
                        int val = Integer.parseInt(s.toString());
                        if (val > 59) {
                            isEditing = true;
                            s.clear();
                            isEditing = false;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        edtSnoozeCount.setText(String.valueOf(currentAlarm.getSnoozeCount()));
        edtSnoozeInterval.setText(String.valueOf(currentAlarm.getSnoozeMinutes()));
        edtMessage.setText(currentAlarm.getMessage());

        btnDays.setText(getString(R.string.alarm_btn_days, currentAlarm.getFormattedDays(this)));
        btnDays.setOnClickListener(v -> showAlarmDaysDialog(currentAlarm, btnDays));

        String rName = currentAlarm.getRingtoneTitle();
        if (rName == null || rName.isEmpty()) {
            rName = getString(R.string.alarm_ringtone_default);
        }
        btnRingtone.setText(getString(R.string.alarm_btn_ringtone, rName));
        btnRingtone.setOnClickListener(v -> showAlarmRingtoneDialog(currentAlarm, btnRingtone));

        String title = isEditMode ? getString(R.string.alarm_dialog_edit_title) : getString(R.string.alarm_dialog_set_title);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(getString(R.string.btn_ok), null)
                .setNegativeButton(getString(R.string.btn_cancel), (d, which) -> {
                    if (returnToList) {
                        showAlarmListDialog();
                    }
                })
                .setOnCancelListener(d -> {
                    if (returnToList) {
                        showAlarmListDialog();
                    }
                })
                .create();

        dialog.setOnShowListener(d -> {
            Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            okButton.setOnClickListener(v -> {
                String hStr = edtHour.getText().toString().trim();
                if (hStr.isEmpty()) {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_alarm_invalid_hour), Toast.LENGTH_SHORT).show();
                    return;
                }
                int hour = Integer.parseInt(hStr);
                if (hour < 0 || hour > 23) {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_alarm_invalid_hour), Toast.LENGTH_SHORT).show();
                    return;
                }

                String mStr = edtMinute.getText().toString().trim();
                if (mStr.isEmpty()) {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_alarm_invalid_minute), Toast.LENGTH_SHORT).show();
                    return;
                }
                int minute = Integer.parseInt(mStr);
                if (minute < 0 || minute > 59) {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_alarm_invalid_minute), Toast.LENGTH_SHORT).show();
                    return;
                }

                int snoozeCount = 3;
                try {
                    String scStr = edtSnoozeCount.getText().toString().trim();
                    if (!scStr.isEmpty()) snoozeCount = Integer.parseInt(scStr);
                } catch (Exception ignored) {}

                int snoozeInterval = 5;
                try {
                    String siStr = edtSnoozeInterval.getText().toString().trim();
                    if (!siStr.isEmpty()) snoozeInterval = Integer.parseInt(siStr);
                } catch (Exception ignored) {}

                String message = edtMessage.getText().toString().trim();

                currentAlarm.setHour(hour);
                currentAlarm.setMinute(minute);
                currentAlarm.setSnoozeCount(snoozeCount);
                currentAlarm.setRemainingSnoozeCount(snoozeCount);
                currentAlarm.setSnoozeMinutes(snoozeInterval);
                currentAlarm.setMessage(message);
                currentAlarm.setEnabled(true);

                AlarmManagerHelper.saveOrUpdateAlarm(MainActivity.this, currentAlarm);
                updateAlarmListButtonVisibility();

                if (isEditMode) {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_alarm_updated), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_alarm_saved, currentAlarm.getFormattedTime()), Toast.LENGTH_SHORT).show();
                }

                dialog.dismiss();
                if (returnToList) {
                    showAlarmListDialog();
                }
            });
        });

        dialog.show();
    }

    private void showAlarmDaysDialog(final AlarmItem alarm, final Button btnDaysRef) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_alarm_days, null);
        Button btnEveryday = dialogView.findViewById(R.id.btnAlarmEveryday);
        CheckBox[] chkDays = {
                dialogView.findViewById(R.id.chkDayMon),
                dialogView.findViewById(R.id.chkDayTue),
                dialogView.findViewById(R.id.chkDayWed),
                dialogView.findViewById(R.id.chkDayThu),
                dialogView.findViewById(R.id.chkDayFri),
                dialogView.findViewById(R.id.chkDaySat),
                dialogView.findViewById(R.id.chkDaySun)
        };

        final boolean[] tempDays = new boolean[7];
        System.arraycopy(alarm.getDaysOfWeek(), 0, tempDays, 0, 7);

        for (int i = 0; i < 7; i++) {
            chkDays[i].setChecked(tempDays[i]);
        }

        btnEveryday.setOnClickListener(v -> {
            boolean allChecked = true;
            for (int i = 0; i < 7; i++) {
                if (!chkDays[i].isChecked()) {
                    allChecked = false;
                    break;
                }
            }
            boolean newState = !allChecked;
            for (int i = 0; i < 7; i++) {
                chkDays[i].setChecked(newState);
                tempDays[i] = newState;
            }
        });

        for (int i = 0; i < 7; i++) {
            final int dayIdx = i;
            chkDays[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                tempDays[dayIdx] = isChecked;
            });
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.alarm_days_title))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.btn_ok), (dialog, which) -> {
                    alarm.setDaysOfWeek(tempDays);
                    btnDaysRef.setText(getString(R.string.alarm_btn_days, alarm.getFormattedDays(MainActivity.this)));
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void showAlarmRingtoneDialog(final AlarmItem alarm, final Button btnRingtoneRef) {
        RingtoneManager rm = new RingtoneManager(this);
        rm.setType(RingtoneManager.TYPE_ALARM);
        Cursor cursor = null;
        try {
            cursor = rm.getCursor();
        } catch (Exception ignored) {}

        final List<String> titles = new ArrayList<>();
        final List<String> uriStrings = new ArrayList<>();

        titles.add(getString(R.string.alarm_ringtone_default));
        uriStrings.add("");

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                    Uri uri = rm.getRingtoneUri(cursor.getPosition());
                    titles.add(title);
                    uriStrings.add(uri != null ? uri.toString() : "");
                }
            } catch (Exception ignored) {}
        }

        int checkedIndex = 0;
        String currentUri = alarm.getRingtoneUri();
        if (currentUri != null && !currentUri.isEmpty()) {
            for (int i = 0; i < uriStrings.size(); i++) {
                if (currentUri.equals(uriStrings.get(i))) {
                    checkedIndex = i;
                    break;
                }
            }
        }

        final int[] selectedIndex = {checkedIndex};
        String[] items = titles.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.alarm_ringtone_title));
        builder.setSingleChoiceItems(items, checkedIndex, (dialog, which) -> {
            selectedIndex[0] = which;
            playAlarmPreview(uriStrings.get(which));
        });

        builder.setPositiveButton(getString(R.string.btn_ok), (dialog, which) -> {
            stopAlarmPreview();
            int idx = selectedIndex[0];
            alarm.setRingtoneUri(uriStrings.get(idx));
            alarm.setRingtoneTitle(titles.get(idx));
            btnRingtoneRef.setText(getString(R.string.alarm_btn_ringtone, titles.get(idx)));
        });

        builder.setNeutralButton(getString(R.string.btn_select_storage), (dialog, which) -> {
            stopAlarmPreview();
            openAlarmStorageFilePicker(alarm, btnRingtoneRef);
        });

        builder.setNegativeButton(getString(R.string.btn_cancel), (dialog, which) -> stopAlarmPreview());
        builder.setOnCancelListener(dialog -> stopAlarmPreview());
        builder.show();
    }

    private void openAlarmStorageFilePicker(AlarmItem alarm, Button btnRingtone) {
        currentEditingAlarm = alarm;
        currentAlarmRingtoneBtnRef = btnRingtone;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            startActivityForResult(intent, REQUEST_CODE_PICK_ALARM_AUDIO);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.setType("audio/*");
                startActivityForResult(fallback, REQUEST_CODE_PICK_ALARM_AUDIO);
            } catch (Exception ignored) {}
        }
    }

    private void playAlarmPreview(String uriStr) {
        stopAlarmPreview();
        try {
            alarmPreviewPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmPreviewPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            } else {
                alarmPreviewPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }

            Uri uri = (uriStr == null || uriStr.isEmpty()) ?
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) :
                    Uri.parse(uriStr);

            if (uri != null) {
                alarmPreviewPlayer.setDataSource(this, uri);
                alarmPreviewPlayer.prepare();
                alarmPreviewPlayer.start();
            }
        } catch (Exception e) {
            stopAlarmPreview();
        }
    }

    private void stopAlarmPreview() {
        try {
            if (alarmPreviewPlayer != null) {
                if (alarmPreviewPlayer.isPlaying()) {
                    alarmPreviewPlayer.stop();
                }
                alarmPreviewPlayer.release();
                alarmPreviewPlayer = null;
            }
        } catch (Exception ignored) {}
    }

    private void showAlarmListDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_alarm_list, null);
        final LinearLayout llMultiSelectActions = dialogView.findViewById(R.id.llMultiSelectActions);
        final Button btnSelectAll = dialogView.findViewById(R.id.btnSelectAll);
        final Button btnDeleteSelected = dialogView.findViewById(R.id.btnDeleteSelected);
        final Button btnCancelMultiSelect = dialogView.findViewById(R.id.btnCancelMultiSelect);
        final TextView txtEmptyAlarms = dialogView.findViewById(R.id.txtEmptyAlarms);
        final LinearLayout llContainer = dialogView.findViewById(R.id.llAlarmListContainer);
        final LinearLayout llNormalActions = dialogView.findViewById(R.id.llNormalActions);
        final Button btnAddAlarm = dialogView.findViewById(R.id.btnAddAlarmFromList);
        final Button btnClose = dialogView.findViewById(R.id.btnCloseAlarmList);

        final boolean[] isMultiSelectMode = {false};
        final Set<Long> selectedIds = new HashSet<>();

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.alarm_list_title))
                .setView(dialogView)
                .create();

        final Runnable renderList = new Runnable() {
            @Override
            public void run() {
                llContainer.removeAllViews();
                List<AlarmItem> alarms = AlarmManagerHelper.loadAlarms(MainActivity.this);

                if (alarms.isEmpty()) {
                    txtEmptyAlarms.setVisibility(View.VISIBLE);
                    llMultiSelectActions.setVisibility(View.GONE);
                    llNormalActions.setVisibility(View.VISIBLE);
                    return;
                } else {
                    txtEmptyAlarms.setVisibility(View.GONE);
                }

                if (isMultiSelectMode[0]) {
                    llMultiSelectActions.setVisibility(View.VISIBLE);
                    llNormalActions.setVisibility(View.GONE);
                } else {
                    llMultiSelectActions.setVisibility(View.GONE);
                    llNormalActions.setVisibility(View.VISIBLE);
                }

                LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                for (final AlarmItem alarm : alarms) {
                    View itemView = inflater.inflate(R.layout.item_alarm, llContainer, false);
                    LinearLayout llNormal = itemView.findViewById(R.id.llNormalMode);
                    Button btnAlarm = itemView.findViewById(R.id.btnAlarmItem);
                    Switch switchAlarm = itemView.findViewById(R.id.switchAlarm);
                    CheckBox chkAlarm = itemView.findViewById(R.id.chkAlarmItem);

                    StringBuilder label = new StringBuilder();
                    label.append(alarm.getFormattedTime()).append("  (").append(alarm.getFormattedDays(MainActivity.this)).append(")");
                    if (!alarm.getMessage().isEmpty()) {
                        label.append("\n").append(alarm.getMessage());
                    }

                    if (!isMultiSelectMode[0]) {
                        llNormal.setVisibility(View.VISIBLE);
                        chkAlarm.setVisibility(View.GONE);
                        btnAlarm.setText(label.toString());
                        switchAlarm.setChecked(alarm.isEnabled());

                        // Click to edit alarm
                        btnAlarm.setOnClickListener(v -> {
                            dialog.dismiss();
                            showAlarmEditDialog(alarm, true);
                        });

                        // Long click to switch into multi-select mode
                        btnAlarm.setOnLongClickListener(v -> {
                            isMultiSelectMode[0] = true;
                            selectedIds.clear();
                            selectedIds.add(alarm.getId());
                            run();
                            return true;
                        });

                        // Toggle switch
                        switchAlarm.setOnCheckedChangeListener((btn, isChecked) -> {
                            alarm.setEnabled(isChecked);
                            AlarmManagerHelper.saveOrUpdateAlarm(MainActivity.this, alarm);
                            String msg = isChecked ?
                                    getString(R.string.toast_alarm_saved, alarm.getFormattedTime()) :
                                    getString(R.string.toast_alarm_dismissed);
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        llNormal.setVisibility(View.GONE);
                        chkAlarm.setVisibility(View.VISIBLE);
                        chkAlarm.setText(label.toString());
                        chkAlarm.setChecked(selectedIds.contains(alarm.getId()));

                        chkAlarm.setOnCheckedChangeListener((btn, isChecked) -> {
                            if (isChecked) {
                                selectedIds.add(alarm.getId());
                            } else {
                                selectedIds.remove(alarm.getId());
                            }
                        });
                    }

                    llContainer.addView(itemView);
                }
            }
        };

        btnSelectAll.setOnClickListener(v -> {
            List<AlarmItem> alarms = AlarmManagerHelper.loadAlarms(MainActivity.this);
            if (selectedIds.size() == alarms.size()) {
                selectedIds.clear();
            } else {
                for (AlarmItem a : alarms) {
                    selectedIds.add(a.getId());
                }
            }
            renderList.run();
        });

        btnDeleteSelected.setOnClickListener(v -> {
            if (selectedIds.isEmpty()) return;
            int count = selectedIds.size();
            AlarmManagerHelper.deleteAlarms(MainActivity.this, new ArrayList<>(selectedIds));
            selectedIds.clear();
            isMultiSelectMode[0] = false;
            updateAlarmListButtonVisibility();
            renderList.run();
            Toast.makeText(MainActivity.this, getString(R.string.toast_alarm_deleted, count), Toast.LENGTH_SHORT).show();
        });

        btnCancelMultiSelect.setOnClickListener(v -> {
            isMultiSelectMode[0] = false;
            selectedIds.clear();
            renderList.run();
        });

        btnAddAlarm.setOnClickListener(v -> {
            dialog.dismiss();
            showAlarmEditDialog(null, true);
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        renderList.run();
        dialog.show();
    }

    // ==================== AUTO UPDATE IMPLEMENTATION ====================

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "";
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private boolean isNewerVersion(String latestVersion, String currentVersion) {
        if (latestVersion == null || latestVersion.isEmpty()) return false;
        if (currentVersion == null || currentVersion.isEmpty()) return true;

        String cleanLatest = latestVersion.replaceAll("^[vV]", "").trim();
        String cleanCurrent = currentVersion.replaceAll("^[vV]", "").trim();

        String[] latestParts = cleanLatest.split("[.\\-_]");
        String[] currentParts = cleanCurrent.split("[.\\-_]");
        int length = Math.max(latestParts.length, currentParts.length);

        for (int i = 0; i < length; i++) {
            int vLatest = 0;
            int vCurrent = 0;
            if (i < latestParts.length) {
                try {
                    String num = latestParts[i].replaceAll("[^0-9].*", "");
                    if (!num.isEmpty()) vLatest = Integer.parseInt(num);
                } catch (Exception ignored) {}
            }
            if (i < currentParts.length) {
                try {
                    String num = currentParts[i].replaceAll("[^0-9].*", "");
                    if (!num.isEmpty()) vCurrent = Integer.parseInt(num);
                } catch (Exception ignored) {}
            }
            if (vLatest > vCurrent) return true;
            if (vLatest < vCurrent) return false;
        }
        return false;
    }

    private void checkForUpdates(boolean isManual) {
        if (isManual) {
            Toast.makeText(this, getString(R.string.toast_checking_update), Toast.LENGTH_SHORT).show();
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.github.com/repos/smartthinh/time-announcer/releases/latest");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "TimeAnnouncer-App");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    byte[] buffer = new byte[2048];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        sb.append(new String(buffer, 0, read));
                    }
                    is.close();

                    JSONObject releaseJson = new JSONObject(sb.toString());
                    String tagName = releaseJson.optString("tag_name", "");
                    String releaseName = releaseJson.optString("name", tagName);
                    String body = releaseJson.optString("body", "");

                    // Find APK asset
                    String apkUrl = "";
                    long apkSize = 0;
                    JSONArray assets = releaseJson.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String assetName = asset.optString("name", "");
                            if (assetName.toLowerCase().endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", "");
                                apkSize = asset.optLong("size", 0);
                                break;
                            }
                        }
                    }

                    String currentVersion = "";
                    try {
                        currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    } catch (Exception ignored) {}

                    final String finalApkUrl = apkUrl;
                    final long finalApkSize = apkSize;
                    final String finalTagName = tagName;
                    final String finalReleaseName = releaseName;
                    final String finalBody = body;
                    final String finalCurrentVersion = currentVersion;

                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        if (isNewerVersion(finalTagName, finalCurrentVersion)) {
                            showUpdateDialog(finalTagName, finalReleaseName, finalBody, finalApkUrl, finalApkSize);
                        } else if (isManual) {
                            Toast.makeText(MainActivity.this, getString(R.string.toast_no_update), Toast.LENGTH_SHORT).show();
                        }
                    });
                } else if (isManual) {
                    runOnUiThread(() -> {
                        if (!isDestroyed() && !isFinishing()) {
                            Toast.makeText(MainActivity.this, getString(R.string.toast_update_check_failed), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (isManual) {
                    runOnUiThread(() -> {
                        if (!isDestroyed() && !isFinishing()) {
                            Toast.makeText(MainActivity.this, getString(R.string.toast_update_check_failed), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private String extractLocalizedChangelog(String fullBody, String targetLang) {
        if (fullBody == null || fullBody.trim().isEmpty()) {
            return "";
        }

        String lowerTarget = (targetLang != null) ? targetLang.toLowerCase(Locale.US) : "en";
        Map<String, StringBuilder> sections = new HashMap<>();
        String currentSection = null;

        String[] lines = fullBody.split("\r?\n");
        for (String line : lines) {
            String trimmed = line.trim();
            // Match language code tags like "## [vi]", "[vi]", "### [en] English", etc.
            if (trimmed.matches("(?i)^#{0,4}\\s*\\[[a-z]{2,3}\\].*")) {
                int start = trimmed.indexOf('[');
                int end = trimmed.indexOf(']');
                if (start != -1 && end > start) {
                    currentSection = trimmed.substring(start + 1, end).toLowerCase(Locale.US);
                    if (!sections.containsKey(currentSection)) {
                        sections.put(currentSection, new StringBuilder());
                    }
                    continue; // Skip the tag header line itself
                }
            }

            if (currentSection != null) {
                StringBuilder sb = sections.get(currentSection);
                if (sb != null) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                }
            }
        }

        // If no language tags were found at all, return the raw body as-is
        if (sections.isEmpty()) {
            return fullBody;
        }

        // 1. Check if the user's current language section exists and is not empty
        if (sections.containsKey(lowerTarget)) {
            String content = sections.get(lowerTarget).toString().trim();
            if (!content.isEmpty()) {
                return content;
            }
        }

        // 2. If app is using another language or if current language section is missing, fallback to English ("en")
        if (sections.containsKey("en")) {
            String content = sections.get("en").toString().trim();
            if (!content.isEmpty()) {
                return content;
            }
        }

        // 3. Fallback to the first available section if "en" is not present
        for (StringBuilder sb : sections.values()) {
            String content = sb.toString().trim();
            if (!content.isEmpty()) {
                return content;
            }
        }

        return fullBody;
    }

    private void showUpdateDialog(String tagName, String releaseName, String body, String apkUrl, long apkSize) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_update, null);
        builder.setView(dialogView);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();

        TextView tvUpdateTitle = dialogView.findViewById(R.id.tvUpdateTitle);
        LinearLayout llChangelogItems = dialogView.findViewById(R.id.llChangelogItems);
        LinearLayout llDownloadProgress = dialogView.findViewById(R.id.llDownloadProgress);
        ProgressBar pbDownloadProgress = dialogView.findViewById(R.id.pbDownloadProgress);
        TextView tvDownloadPercent = dialogView.findViewById(R.id.tvDownloadPercent);
        TextView tvDownloadBytes = dialogView.findViewById(R.id.tvDownloadBytes);
        Button btnDownloadUpdate = dialogView.findViewById(R.id.btnDownloadUpdate);
        Button btnCancelUpdate = dialogView.findViewById(R.id.btnCancelUpdate);

        String titleText = getString(R.string.dialog_update_title, (releaseName != null && !releaseName.isEmpty()) ? releaseName : tagName);
        tvUpdateTitle.setText(titleText);

        // Extract localized changelog according to current app language
        String changelog = extractLocalizedChangelog(body, currentLanguage);

        // Split changelog by newline: each line is an individual element
        llChangelogItems.removeAllViews();
        if (changelog != null && !changelog.trim().isEmpty()) {
            String[] lines = changelog.split("\r?\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    View spacer = new View(this);
                    spacer.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(6)));
                    llChangelogItems.addView(spacer);
                    continue;
                }
                TextView itemTv = new TextView(this);
                itemTv.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                itemTv.setTextSize(14f);
                itemTv.setTextColor(0xFF333333);
                itemTv.setPadding(0, dpToPx(2), 0, dpToPx(2));

                if (trimmed.startsWith("#")) {
                    itemTv.setText(trimmed.replaceAll("^#+\\s*", ""));
                    itemTv.setTypeface(Typeface.DEFAULT_BOLD);
                    itemTv.setTextSize(15f);
                    itemTv.setTextColor(0xFF1976D2);
                    itemTv.setPadding(0, dpToPx(8), 0, dpToPx(4));
                } else {
                    itemTv.setText(trimmed);
                }
                llChangelogItems.addView(itemTv);
            }
        }

        if (apkUrl == null || apkUrl.isEmpty()) {
            btnDownloadUpdate.setEnabled(false);
        }

        btnDownloadUpdate.setOnClickListener(v -> {
            if (apkUrl == null || apkUrl.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_download_failed), Toast.LENGTH_SHORT).show();
                return;
            }
            btnDownloadUpdate.setVisibility(View.GONE);
            llDownloadProgress.setVisibility(View.VISIBLE);
            pbDownloadProgress.setProgress(0);
            tvDownloadPercent.setText("0%");
            if (apkSize > 0) {
                tvDownloadBytes.setText("0 B / " + formatFileSize(apkSize));
            }

            startDownloadApk(apkUrl, apkSize, pbDownloadProgress, tvDownloadPercent, tvDownloadBytes, dialog);
        });

        btnCancelUpdate.setOnClickListener(v -> {
            isDownloadCancelled = true;
            if (downloadThread != null && downloadThread.isAlive()) {
                downloadThread.interrupt();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void startDownloadApk(String downloadUrl, long expectedSize,
                                  ProgressBar pb, TextView tvPercent, TextView tvBytes,
                                  AlertDialog dialog) {
        isDownloadCancelled = false;
        downloadThread = new Thread(() -> {
            File cacheDir = getCacheDir();
            File apkFile = new File(cacheDir, "update.apk");
            if (apkFile.exists()) {
                apkFile.delete();
            }

            InputStream input = null;
            FileOutputStream output = null;
            HttpURLConnection conn = null;

            try {
                String currentUrl = downloadUrl;
                int redirects = 0;
                while (redirects < 8) {
                    URL url = new URL(currentUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "TimeAnnouncer-App");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.connect();

                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                            || code == 307 || code == 308) {
                        String newUrl = conn.getHeaderField("Location");
                        if (newUrl != null) {
                            currentUrl = newUrl;
                            redirects++;
                            conn.disconnect();
                            continue;
                        }
                    }
                    break;
                }

                long totalBytes = conn.getContentLength();
                if (totalBytes <= 0) {
                    totalBytes = expectedSize;
                }
                final long finalTotal = totalBytes;

                input = conn.getInputStream();
                output = new FileOutputStream(apkFile);

                byte[] data = new byte[8192];
                long downloadedBytes = 0;
                int count;
                long lastUpdateTime = 0;

                while ((count = input.read(data)) != -1) {
                    if (isDownloadCancelled || Thread.currentThread().isInterrupted()) {
                        apkFile.delete();
                        return;
                    }
                    output.write(data, 0, count);
                    downloadedBytes += count;

                    long now = System.currentTimeMillis();
                    if (now - lastUpdateTime > 50 || downloadedBytes == finalTotal) {
                        lastUpdateTime = now;
                        final long curDownloaded = downloadedBytes;
                        final int percent = (finalTotal > 0) ? (int) ((curDownloaded * 100) / finalTotal) : 0;
                        runOnUiThread(() -> {
                            if (!isDestroyed() && !isFinishing() && dialog.isShowing()) {
                                pb.setProgress(percent);
                                tvPercent.setText(percent + "%");
                                if (finalTotal > 0) {
                                    tvBytes.setText(formatFileSize(curDownloaded) + " / " + formatFileSize(finalTotal));
                                } else {
                                    tvBytes.setText(formatFileSize(curDownloaded));
                                }
                            }
                        });
                    }
                }

                output.flush();

                runOnUiThread(() -> {
                    if (!isDestroyed() && !isFinishing()) {
                        pb.setProgress(100);
                        tvPercent.setText("100%");
                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        installApk(apkFile);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                if (!isDownloadCancelled) {
                    runOnUiThread(() -> {
                        if (!isDestroyed() && !isFinishing()) {
                            Toast.makeText(MainActivity.this, getString(R.string.toast_download_failed), Toast.LENGTH_SHORT).show();
                            if (dialog.isShowing()) {
                                dialog.dismiss();
                            }
                        }
                    });
                }
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (Exception ignored) {}
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
        downloadThread.start();
    }

    private void installApk(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(this, getString(R.string.toast_download_failed), Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this, getString(R.string.toast_permission_install_required), Toast.LENGTH_LONG).show();
                Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(permIntent);
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Uri apkUri = GenericFileProvider.getUriForFile(apkFile);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        } else {
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
        }

        try {
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.toast_download_failed), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPreviewSound();
        stopAlarmPreview();
        if (previewTts != null) {
            previewTts.stop();
            previewTts.shutdown();
        }
        isDownloadCancelled = true;
        if (downloadThread != null && downloadThread.isAlive()) {
            downloadThread.interrupt();
        }
    }

    static class SoundItem {
        String name;
        String path;
        boolean isAsset;

        SoundItem(String name, String path, boolean isAsset) {
            this.name = name;
            this.path = path;
            this.isAsset = isAsset;
        }
    }
}
