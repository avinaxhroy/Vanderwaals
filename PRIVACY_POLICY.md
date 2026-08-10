# Privacy policy for Vanderwaals

**Last updated:** July 16, 2026

Vanderwaals is an open-source Android wallpaper application that personalizes wallpaper recommendations using on-device machine learning. This policy explains how data is handled on your device.

The app is built around privacy-first principles: machine learning inference runs locally on your device, and the app contains zero analytics, telemetry, tracking, or advertising SDKs.

---

## 1. Data we do not collect

Vanderwaals does not collect, transmit, or store any of the following:

- Personal identifiable information (name, email address, phone number)
- Account credentials (the app has no user accounts or registration)
- Device identifiers (advertising ID, Android ID, IMEI, or serial numbers)
- Geolocation data
- Contacts, call logs, calendar events, or messages
- Unselected photos from your device gallery
- Browsing history, analytics, or usage telemetry
- Crash reports or performance analytics

The application contains no third-party tracking or advertising SDKs.

---

## 2. Data stored locally on your device

The following data is stored exclusively in the app's local database and private storage directory:

| Data | Purpose | Storage location |
|------|---------|------------------|
| Preference vector | 1280-dimensional mathematical vector representing visual style preferences | App database |
| Rating history | Log of explicit likes and dislikes used to update recommendations | App database |
| Application history | Record of applied wallpapers displayed in the History screen | App database |
| Cached wallpapers | Downloaded image files retained for offline wallpaper changes | Local application cache |

Local data is not backed up to cloud services (`android:allowBackup="false"`). Uninstalling the application permanently removes all local data.

---

## 3. Data processed for personalization

When you use Personalize Mode to select reference images:

- Images are processed locally by MobileNetV4-Conv-Small to extract feature vectors.
- Images are processed in memory and discarded; raw image bytes are not uploaded or transmitted to external servers.
- Only the computed 1280-dimensional preference vector is retained in local storage.

---

## 4. Network usage

The application uses internet access exclusively to fetch public wallpaper image files and catalog manifests from:

- GitHub-hosted Vanderwaals repositories
- Bing public daily photography archives

Network requests fetch public static files only. No personal data, device identifiers, or preference vectors are included in network requests.

---

## 5. Required permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | Download public wallpaper files and catalog manifests |
| `ACCESS_NETWORK_STATE` | Verify network connectivity before initiating downloads |
| `SET_WALLPAPER` | Set selected images as system or lock screen wallpapers |
| `WAKE_LOCK` | Ensure CPU remains active briefly while applying wallpapers |
| `POST_NOTIFICATIONS` | Display foreground service notifications during wallpaper updates (Android 13+) |
| `FOREGROUND_SERVICE` | Maintain scheduled wallpaper changes when the app is closed |
| `RECEIVE_BOOT_COMPLETED` | Reschedule wallpaper changes after device restarts |
| `SCHEDULE_EXACT_ALARM` | Trigger wallpaper rotation at specified times |

---

## 6. Open source verification

Vanderwaals is open-source software. You can inspect the source code and build instructions at:

`https://github.com/avinaxhroy/Vanderwaals`

---

## 7. Contact

Questions regarding this privacy policy or application data handling can be submitted to:

- **Email**: hi@avinas.me
- **Issue tracker**: `https://github.com/avinaxhroy/Vanderwaals/issues`
