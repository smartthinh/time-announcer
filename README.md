# Time Announcer

An open-source Android application that automatically announces the current time via voice speech synthesis (Text-to-Speech), plays chime sounds according to your configured schedule, and provides a full-featured customizable alarm clock with speech synthesis.

## Overview

Time Announcer is designed for users who want to stay informed of the time hands-free, without having to look at their phone screen. Whether you are working, studying, exercising, cooking, or managing daily routines, the app announces the exact time periodically using your device Text-to-Speech engine and optional chime audio.

In addition, Time Announcer includes a robust alarm clock with exact time scheduling, custom snooze repeat counts and intervals, Text-to-Speech announcement reading, recurring day schedules, system ringtone and internal storage audio selection, continuous audio looping, 10-minute automatic silence, and interactive batch alarm management.

The application runs reliably in the background as a foreground service and requests battery optimization exemption so that scheduled announcements and alarms fire accurately even when the device screen is turned off.

## Key Features

- Auto Speak Time: Toggle background time announcement on or off. When enabled, requests battery optimization exemption to prevent the operating system from terminating the background service during sleep.
- Configurable Announcement Interval: Choose how often the time is announced within each hour: 5 minutes, 10 minutes, 15 minutes, 20 minutes, 30 minutes, or 60 minutes.
- Selective Announcement Hours: Choose specifically which hours of the day (from 0 to 23) should trigger announcements. Unselected hours remain quiet.
- Complete TTS Engine Selection: Detects and lists all Text-to-Speech engines installed on your device (such as Google Speech Services, Samsung TTS, Vocalizer, RHVoice, etc.).
- System Language Voice Filtering: When selecting a voice, the app automatically filters and displays only voices that match your device current system language, eliminating irrelevant language entries.
- Speech Parameter Controls: Fine-tune speech rate (supporting up to 5.0x), pitch, and voice volume with clean, accessible slider labels. Voice volume defaults to 50% to provide headroom for users who need amplified loudness up to 100%.
- Five Audio Streams: Route speech and chime playback through five separate system audio channels: Media volume stream, Ring volume stream, Alarm volume stream, Accessibility volume stream, or Notification volume stream.
- Chime Audio Playback: Optionally play a chime before reading the time. Includes 18 bundled chime sounds and supports picking any custom audio file from your device internal storage.
- Vibrate with Announcement: Optionally vibrate whenever the time announcement triggers, keeping you informed even in noisy environments or discreet situations.
- Custom Voice Pack Support: Import personalized voice recordings via .zip packages containing `hour`, `hourly`, and `minutes` directories (metadata JSON is optional; the package automatically uses the ZIP file name if JSON is omitted). Enjoy natural and completely seamless (gapless) voice announcements.
- Countdown Timer:
  - Create and configure timers with custom names, duration in minutes, optional completion message read via TTS, independent sound and vibration controls.
  - Sound selection supporting all bundled chime sounds as well as custom audio files chosen directly from device internal storage.
  - Dedicated controller dialog with real-time countdown display and full playback lifecycle controls: Start, Pause, Resume, and End permanently (resets countdown).
  - Background execution with live notification updates and full Doze/screen-off exact alarm reliability.
  - Interactive timer manager dialog supporting tap-to-control, long-press multi-select mode with "Select all", "Delete", and a contextual "Edit" button that automatically appears only when exactly one timer is selected.
  - Completion alert dialog waking the screen with continuous ringing, rhythmic vibration, optional TTS repeated reading, and an easy one-tap dismiss button.
- Comprehensive Alarm Clock:
  - Exact time scheduling with hours (0-23) and minutes (0-59) with auto-fill of current time and strict input validation.
  - Inline input hints for hour, minute, snooze repeats, snooze interval, and TTS message.
  - Configurable snooze count and snooze delay interval.
  - Optional TTS announcement message read aloud repeatedly while the alarm is ringing.
  - Flexible day recurrence dialog with 7 day-of-week checkboxes (defaults to today).
  - Ringtone picker featuring all default system alarm ringtones with instant playback preview, plus picking any music file from device internal storage.
  - Continuous ringtone looping so melodies never cut off mid-alarm.
  - Dedicated sound and vibration toggles with continuous, looping alarm vibration.
  - Automatic silence protection after 10 minutes to preserve battery and prevent speaker fatigue.
  - Ringing alarm dialog with Snooze ("Dừng tạm thời") and Dismiss ("Tắt báo thức") controls.
  - Configured alarms dialog displaying alarms as interactive buttons with individual ON/OFF toggle switches, tap-to-edit, and long-press multi-select mode for batch deletion.
  - Seamless navigation returning directly to the configured alarms list after editing.
  - Rescheduling across device reboots (`BOOT_COMPLETED`).
- Silent Error Handling: If an audio file is missing or corrupted, the app skips the sound silently and continues without interrupting your experience or showing error dialogs.
- Clean and Accessible Interface: All controls are organized cleanly with accessible dialogs, clear labels, and no extraneous visual clutter.

## Requirements

- Operating System: Android 5.0 (API level 21) or higher
- Target SDK: Android 14 (API level 34)
- Package Name: com.smartthinh.timeannouncer
- Text-to-Speech: A working TTS engine installed on the device

## How to Use

### 1. Granting Permissions on First Launch
When opening the application for the first time, grant the necessary system permissions when prompted (post notifications on Android 13+, battery optimization exemption, and exact alarms on Android 12+). These permissions ensure that scheduled announcements and alarms fire accurately in the background even when the device screen is off.

### 2. Activating Auto Speak Time
Check the Auto speak time checkbox to start background periodic time announcements according to your configured schedule.

### 3. Setting Announcement Interval
Tap the Announcement interval button to open the interval selection dialog. Choose your preferred interval:
- 5 minutes: Announces at :00, :05, :10, :15, :20, :25, :30, :35, :40, :45, :50, :55
- 10 minutes: Announces at :00, :10, :20, :30, :40, :50
- 15 minutes: Announces at :00, :15, :30, :45
- 20 minutes: Announces at :00, :20, :40
- 30 minutes: Announces at :00, :30
- 60 minutes: Announces at the top of each hour (:00)

### 4. Selecting Hours to Announce
Tap the Hours to announce button to open the 24-hour checklist (0:00 to 23:00). Check only the hours during which you wish to receive announcements. For example, you can select daytime hours from 7:00 to 22:00 and leave night hours unchecked to ensure quiet sleep. A Select all button is available for quick configuration.

### 5. Configuring TTS and Voice
Tap the TTS engine settings button (always accessible on the main screen) to customize speech delivery:
- Select TTS engine: View all installed speech synthesis engines and switch between them.
- Select voice: View and select from voices that match your system language.
- Speech rate: Adjust reading speed flexibly from 0.5x up to 5.0x.
- Pitch: Adjust voice tone from deep to high.
- Speech volume: Set volume between 0% and 100% (default is 50% for amplification headroom).
- Select audio stream: Choose which audio stream handles speech playback (Media, Ring, Alarm, Accessibility, or Notification).
- Test voice: Tap to hear an instant sample with your current settings.

### 6. Enabling Announcement Sounds
Check the Play sound with announcement checkbox if you want a chime or alert sound to play immediately before the time is spoken:
- Tap Select sound to choose from the list of bundled sound files (such as bell, chime, clock chimes, cuckoo sounds, etc.).
- Tap Select from device storage inside the dialog to pick your own custom audio file (MP3, WAV, OGG, etc.) from device storage.
- Tapping any sound in the dialog plays a quick preview.

### 7. Setting Alarms and Managing Alarms
Tap the Set alarm button on the main screen to open the alarm manager dialog:
- View all scheduled alarms as interactive buttons with individual ON/OFF toggle switches.
- Tap an alarm to edit it. After saving or canceling, you are smoothly returned to the alarm manager dialog.
- Long-press any alarm to switch into multi-select mode with checkboxes to select all and batch-delete alarms.
- Tap the Set alarm button inside the dialog to create a new alarm:
  - Current hour and minute are auto-filled. Input limits are strictly enforced (0-23 for hours, 0-59 for minutes).
  - Set the number of snooze repetitions and the snooze interval in minutes.
  - Optionally enter text in the announcement field for Text-to-Speech to read aloud while the alarm is ringing.
  - Tap Repeat days to open the recurrence dialog and select any combination of days using the day checkboxes (defaults to the current day of the week).
  - Use the Sound checkbox to enable or mute the ringtone, and tap Ringtone to select from default device alarm sounds or pick any custom audio file from your device internal storage.
  - Use the Vibrate checkbox to enable or disable continuous rhythmic alarm vibration throughout ringing.
  - When the alarm rings, the screen wakes up and displays the alarm alert dialog with Snooze and Dismiss buttons. The ringtone and vibration loop continuously and automatically silence after 10 minutes if unattended.

### 8. Using the Countdown Timer
Tap the Timer button on the main screen to open the timer manager dialog:
- View all saved timers with their configured duration, current state, and remaining time.
- Tap any timer to open the controller countdown dialog:
  - Tap Start to begin counting down.
  - While counting down, the action button dynamically changes to Pause.
  - While paused, tap Resume to continue counting down.
  - Tap End permanently to completely stop and reset the timer back to its initial duration. After ending, you can tap Start again whenever you wish.
  - Tap Close to dismiss the controller dialog; the timer continues counting down accurately in the background.
- Long-press any timer to switch into multi-select mode:
  - Use Select all to select or deselect all timers at once.
  - Tap Delete to remove all selected timers.
  - When exactly ONE timer is selected, the Edit button appears, allowing you to edit that timer's name, minutes, notification text, sound, and vibration settings. If more than one or zero timers are selected, the Edit button automatically hides.
- Tap Create timer to configure a new timer with name, duration in minutes, completion message, sound toggle (with bundled or internal storage audio picker), and vibration toggle.
- When the countdown finishes, the screen turns on to display the alert dialog, playing the chosen alert sound and vibrating continuously until you tap Dismiss.

### 9. Checking for Updates
The app automatically checks for new updates in the background each time you launch it. You can also tap the Check for updates button on the main screen to check manually at any time:
- If your app is up to date, a message notifies you that you are using the latest version (when checking manually).
- If an update is available, a dialog displays the release title and detailed changelog notes line-by-line.
- Tap Download to start downloading the update with a smooth, real-time 0% to 100% progress bar and size counter.
- Once downloaded, the application automatically prompts to install the new update.

### 10. Information and Support
Tap the About button at the bottom of the main screen to access:
- About: Application summary.
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
│           │               ├── AlarmAlertActivity.java
│           │               ├── AlarmItem.java
│           │               ├── AlarmManagerHelper.java
│           │               ├── AlarmReceiver.java
│           │               ├── BootReceiver.java
│           │               ├── GenericFileProvider.java
│           │               ├── MainActivity.java
│           │               ├── TimeAnnouncementService.java
│           │               ├── TimerAlertActivity.java
│           │               ├── TimerItem.java
│           │               ├── TimerManagerHelper.java
│           │               ├── TimerReceiver.java
│           │               └── TimerService.java
│           └── res/
│               ├── layout/
│               │   ├── activity_main.xml
│               │   ├── dialog_about.xml
│               │   ├── dialog_alarm_alert.xml
│               │   ├── dialog_alarm_days.xml
│               │   ├── dialog_alarm_edit.xml
│               │   ├── dialog_alarm_list.xml
│               │   ├── dialog_custom_voice.xml
│               │   ├── dialog_timer_alert.xml
│               │   ├── dialog_timer_controller.xml
│               │   ├── dialog_timer_edit.xml
│               │   ├── dialog_timer_list.xml
│               │   ├── dialog_tts_settings.xml
│               │   ├── dialog_update.xml
│               │   ├── item_alarm.xml
│               │   └── item_timer.xml
│               ├── values/
│               │   └── strings.xml      # English resources
│               ├── values-vi/
│               │   └── strings.xml      # Vietnamese resources
│               └── xml/
│                   └── file_paths.xml
├── sounds/                              # Audio source files
├── build_apk.bat
├── build_apk.py
├── .gitignore
└── README.md
```

## Permissions Used

- android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: Prompts the system dialog so the app is exempt from background power saving and Doze restrictions.
- android.permission.FOREGROUND_SERVICE: Required to run the continuous background announcer service.
- android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK: Declares background media and speech playback capability under Android 14.
- android.permission.WAKE_LOCK: Prevents the CPU from sleeping at announcement and alarm trigger moments.
- android.permission.POST_NOTIFICATIONS: Displays service notifications and high-priority alarm alerts on Android 13 and newer.
- android.permission.SCHEDULE_EXACT_ALARM: Allows scheduling exact alarms with AlarmManager on Android 12+.
- android.permission.USE_EXACT_ALARM: Ensures alarm clock privileges for exact wake-ups on Android 13+.
- android.permission.VIBRATE: Enables device vibration when an alarm rings.
- android.permission.RECEIVE_BOOT_COMPLETED: Automatically reschedules all active alarms upon device reboot.
- android.permission.USE_FULL_SCREEN_INTENT: Allows launching the alarm alert dialog over the lock screen.
- android.permission.INTERNET: Required to check for and download app updates from GitHub Releases.
- android.permission.REQUEST_INSTALL_PACKAGES: Allows prompting the package installer to install downloaded update APKs.

## License

This project is licensed under the GNU General Public License v3 (GPL v3).

You are free to use, study, modify, and distribute this software. Under the terms of the GPL v3 license, any redistributed or modified versions of this application must also be made open source under the same GPL v3 license, with all original copyright notices preserved.
