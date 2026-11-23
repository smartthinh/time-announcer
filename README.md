# Time Announcer

An open-source Android application that automatically announces the current time via voice speech synthesis (Text-to-Speech) and plays chime sounds according to your configured schedule.

## Overview

Time Announcer is designed for users who want to stay informed of the time hands-free, without having to look at their phone screen. Whether you are working, studying, exercising, cooking, or managing daily routines, the app announces the exact time periodically using your device Text-to-Speech engine and optional chime audio.

The application runs reliably in the background as a foreground service and requests battery optimization exemption so that scheduled announcements fire accurately even when the device screen is turned off.

## Key Features

- Auto Speak Time: Toggle background time announcement on or off. When enabled, requests battery optimization exemption to prevent the operating system from terminating the background service during sleep.
- Configurable Announcement Interval: Choose how often the time is announced within each hour: 5 minutes, 10 minutes, 15 minutes, 20 minutes, 30 minutes, or 60 minutes.
- Selective Announcement Hours: Choose specifically which hours of the day (from 0 to 23) should trigger announcements. Unselected hours remain quiet.
- Complete TTS Engine Selection: Detects and lists all Text-to-Speech engines installed on your device (such as Google Speech Services, Samsung TTS, Vocalizer, RHVoice, etc.).
- System Language Voice Filtering: When selecting a voice, the app automatically filters and displays only voices that match your device current system language, eliminating irrelevant language entries.
- Speech Parameter Controls: Fine-tune speech rate, pitch, and voice volume. Voice volume defaults to 50% to provide headroom for users who need amplified loudness up to 100%.
- Five Audio Streams: Route speech and chime playback through five separate system audio channels: Media volume stream, Ring volume stream, Alarm volume stream, Accessibility volume stream, or Notification volume stream.
- Chime Audio Playback: Optionally play a chime before reading the time. Includes 18 bundled chime sounds and supports picking any custom audio file from your device internal storage.
- Silent Error Handling: If an audio file is missing or corrupted, the app skips the sound silently and continues to announce the time without interrupting your experience or showing error dialogs.
- Clean and Accessible Interface: All controls are organized cleanly with accessible dialogs, clear labels, and no extraneous visual clutter.

## Requirements

- Operating System: Android 5.0 (API level 21) or higher
- Target SDK: Android 14 (API level 34)
- Package Name: com.smartthinh.timeannouncer
- Text-to-Speech: A working TTS engine installed on the device

## How to Use

### 1. Activating Auto Speak Time
Open the application and check the Auto speak time checkbox. When prompted, grant the request to ignore battery optimizations. This permission allows the service to stay active in the background when the screen is locked. On Android 13 or newer, allow notification permission when prompted so the persistent background service notification can be displayed.

### 2. Setting Announcement Interval
Tap the Announcement interval button to open the interval selection dialog. Choose your preferred interval:
- 5 minutes: Announces at :00, :05, :10, :15, :20, :25, :30, :35, :40, :45, :50, :55
- 10 minutes: Announces at :00, :10, :20, :30, :40, :50
- 15 minutes: Announces at :00, :15, :30, :45
- 20 minutes: Announces at :00, :20, :40
- 30 minutes: Announces at :00, :30
- 60 minutes: Announces at the top of each hour (:00)

### 3. Selecting Hours to Announce
Tap the Hours to announce button to open the 24-hour checklist (0:00 to 23:00). Check only the hours during which you wish to receive announcements. For example, you can select daytime hours from 7:00 to 22:00 and leave night hours unchecked to ensure quiet sleep. A Select all button is available for quick configuration.

### 4. Configuring TTS and Voice
Tap the TTS engine settings button to customize speech delivery:
- Select TTS engine: View all installed speech synthesis engines and switch between them.
- Select voice: View and select from voices that match your system language.
- Speech rate: Adjust reading speed from 0.5x to 2.5x.
- Pitch: Adjust voice tone from deep to high.
- Speech volume: Set volume between 0% and 100% (default is 50% for amplification headroom).
- Select audio stream: Choose which audio stream handles speech playback (Media, Ring, Alarm, Accessibility, or Notification).
- Test voice: Tap to hear an instant sample with your current settings.

### 5. Enabling Announcement Sounds
Check the Play sound with announcement checkbox if you want a chime or alert sound to play immediately before the time is spoken:
- Tap Select sound to choose from the list of bundled sound files (such as bell, chime, clock chimes, cuckoo sounds, etc.).
- Tap Select from device storage inside the dialog to pick your own custom audio file (MP3, WAV, OGG, etc.) from device storage.
- Tapping any sound in the dialog plays a quick preview.

### 6. Information and Support
Tap the About button at the bottom of the main screen to access:
- About: Application summary and version details.
- User guide: Quick reference explaining core features.
- License: GNU General Public License v3 terms and source code redistribution requirements.
- Source code: Direct link to the official GitHub project repository.

## Project Structure

```text
time-announcer/
├── app/
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── assets/
│           │   └── sounds/              # Bundled chime audio assets
│           ├── java/
│           │   └── com/
│           │       └── smartthinh/
│           │           └── timeannouncer/
│           │               ├── MainActivity.java
│           │               └── TimeAnnouncementService.java
│           └── res/
│               ├── layout/
│               │   ├── activity_main.xml
│               │   ├── dialog_about.xml
│               │   └── dialog_tts_settings.xml
│               └── values/
│                   └── strings.xml
├── sounds/                              # Audio source files
├── .gitignore
└── README.md
```

## Permissions Used

- android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: Prompts the system dialog so the app is exempt from background power saving and Doze restrictions.
- android.permission.FOREGROUND_SERVICE: Required to run the continuous background announcer service.
- android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK: Declares background media and speech playback capability under Android 14.
- android.permission.WAKE_LOCK: Prevents the CPU from sleeping at the exact announcement trigger moments.
- android.permission.POST_NOTIFICATIONS: Displays the required persistent service notification on Android 13 and newer.

## License

This project is licensed under the GNU General Public License v3 (GPL v3).

You are free to use, study, modify, and distribute this software. Under the terms of the GPL v3 license, any redistributed or modified versions of this application must also be made open source under the same GPL v3 license, with all original copyright notices preserved.
