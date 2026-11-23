package com.smartthinh.timeannouncer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    public static final String PREFS_NAME = "TimeAnnouncerPrefs";
    private static final int REQUEST_CODE_PICK_AUDIO = 202;

    private CheckBox chkAutoSpeak;
    private Button btnInterval;
    private Button btnHours;
    private Button btnTtsSettings;

    // Checkbox and button for announcement sound
    private CheckBox chkPlaySound;
    private Button btnSelectSound;

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
        btnLanguage = findViewById(R.id.btnLanguage);
        btnAbout = findViewById(R.id.btnAbout);

        for (int i = 0; i < 24; i++) {
            selectedHours[i] = true;
        }

        loadPreferences();
        initPreviewTts(selectedEngine, null);
        updateUIState(chkAutoSpeak.isChecked());

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

        // Button "Language" (above About button)
        btnLanguage.setOnClickListener(v -> showLanguageDialog());

        // Button "About"
        btnAbout.setOnClickListener(v -> showAboutDialog());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPreviewSound();
        if (previewTts != null) {
            previewTts.stop();
            previewTts.shutdown();
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
