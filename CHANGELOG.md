# Changelog - Time Announcer

All notable changes, new features, and improvements for **Time Announcer** are documented in this file.

---

## [Version 1.1] - 2026-11-25

A major update adding a comprehensive Alarm Clock feature, seamless permission handling on first launch, automatic in-app updates, and a cleaner user interface.

### New Features:
- **Full-Featured Alarm Clock:**
  - **Accurate Alarm Scheduling:** Easily set hours and minutes. When creating a new alarm, the current device time is automatically filled in for you.
  - **Input Validation:** Automatically prevents typing invalid times (limits hours to 0–23 and minutes to 0–59, instantly clearing invalid entries).
  - **Smart Snooze & Repeat Controls:** Customize the maximum number of snooze repetitions (default: 3 times) and the snooze interval in minutes (default: 5 minutes).
  - **Voice Announcements (Text-to-Speech):** Optionally enter a custom message (e.g., *"Time to wake up and get ready"*). When the alarm rings, your TTS engine reads the message aloud continuously with a 1-second pause between readings.
  - **Flexible Repeat Days:** Includes an easy-to-use **Every day** button to select all days with one tap, or check individual days from Monday to Sunday (automatically selects today by default).
  - **Rich Ringtone Options:** Choose from all default system alarm ringtones with instant playback preview, or pick any music file from your device internal storage.
  - **Continuous Melody Looping:** Ringtones repeat seamlessly while ringing so the audio never stops unexpectedly.
  - **10-Minute Auto-Silence:** If an alarm is left unattended, it automatically silences after 10 minutes to protect your battery and device speakers.
  - **Intuitive Ringing Screen:** Displays large, legible time with two clear action buttons: **Snooze** (to ring again later) and **Dismiss** (to turn off the alarm completely).

- **Interactive Alarm Manager:**
  - View all your scheduled alarms in a neat list with individual ON/OFF toggle switches.
  - Tap any alarm to view and edit its settings (smoothly returning to the alarm list once saved or canceled).
  - Long-press any alarm to switch into multi-select mode with checkboxes, allowing you to select all and batch-delete alarms easily.

- **Automatic Permission Request on First Launch:**
  - When opening the app for the first time, necessary permissions (notifications, battery optimization exemption, and exact alarms) are requested automatically. You no longer need to find and enable the "Auto speak time" checkbox first to grant them.

- **Automatic Alarm Recovery After Reboot:**
  - All enabled alarms are automatically rescheduled whenever you restart your device.

- **Persistent Auto Time Announcement Across Reboots:**
  - The auto speak time service now automatically restores and resumes announcing the time after restarting or turning on the phone, without requiring manual toggle of the checkbox.

- **Direct Boot & Deep Sleep Resilience:**
  - Integrated `directBootAware` and device-protected storage so both alarms and time announcements function accurately even before the first user unlock after a reboot.
  - Added exact `AlarmManager` system wakeups to guarantee time announcements trigger reliably when the screen is off and in deep Doze mode.
  - Added listeners for network time updates and timezone changes (`TIME_SET`, `TIMEZONE_CHANGED`).

- **In-App GitHub Auto-Update:**
  - **Automatic Check on Startup:** The app now automatically checks for updates quietly in the background whenever you open it. If a newer version is found, an update window will appear to let you know.
  - **Manual Check:** You can also manually check for updates at any time by tapping the **Check for updates** button on the main screen.
  - **Clear Update Details:** When an update is available, you can read what is new line-by-line right inside the app before choosing to update.
  - **One-Tap Download & Install:** Download updates directly with a smooth 0% to 100% progress bar, and automatically open the installer as soon as the download finishes.

### UI & Usability Improvements:
- **Streamlined Main Screen:** Simplified the main screen by combining alarm creation and management into a single, clean **"Set alarm"** button, and added the dedicated **"Check for updates"** button.
- **Cleaner About Dialog:** Removed hardcoded version numbers from the About description in both English and Vietnamese for a cleaner, unified presentation.
- **Inline Input Hints:** Removed redundant label text lines above input fields and embedded labels directly inside the fields as hints (Hour, Minute, Snooze repeats, etc.), creating a much cleaner interface.
- **Updated Documentation & Translations:** Updated the User Guide, About dialogs, and `README.md` with full English and Vietnamese localization.

---

## [Version 1.0] - 2026-11-24

Initial release of the Time Announcer application.

### Key Features:
- **Automatic Voice Time Announcements:** Periodically announces the current time using Text-to-Speech without needing to look at or unlock your phone.
- **Customizable Intervals:** Choose announcement frequency every 5, 10, 15, 20, 30, or 60 minutes (top of the hour).
- **24-Hour Checklist:** Select which hours of the day (from 0 to 23) should trigger announcements, keeping your night hours quiet.
- **Text-to-Speech Customization:**
  - Detects all TTS engines installed on your device (Google Speech Services, Samsung TTS, Vocalizer, etc.).
  - Filters voices to match your device current system language.
  - Fine-tune speech rate, pitch, and speech volume up to 100%.
- **5 Audio Channels:** Route audio through Media, Ring, Alarm, Accessibility, or Notification volume streams.
- **Announcement Chime Sounds:** Play optional chimes before time announcements, including 18 bundled chime sounds or custom audio files from internal storage.
- **Reliable Background Service:** Operates as a foreground service with battery optimization exemption to prevent the OS from terminating it when the screen is locked.
- **Multilingual Support:** Clean bilingual support in English and Vietnamese.
