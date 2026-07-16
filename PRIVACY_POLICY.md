# Privacy Policy for Vanderwaals

**Last updated:** July 16, 2026

Vanderwaals ("the app", "we", "us") is an open-source Android wallpaper app that learns your aesthetic preferences using on-device machine learning. This policy explains what data the app does and does not collect, and how it behaves on your device.

By design, Vanderwaals is a **privacy-first** application: machine learning runs entirely on your phone, the app contains no analytics, no advertising, and no tracking, and it never sells or shares personal data with anyone.

## 1. Data we do NOT collect

Vanderwaals does **not** collect, transmit, or store any of the following:

- Personal identifiable information (name, email, phone number, address)
- Account credentials &mdash; the app has no login and no accounts
- Device identifiers (advertising ID, Android ID, IMEI, serial number)
- Geolocation or precise location
- Contacts, messages, call logs, or calendar data
- Photos from your gallery &mdash; only wallpapers you explicitly choose to upload are processed (see Section 3)
- Browsing history or usage analytics
- Crash reports or performance telemetry

The app contains **no analytics SDKs, no ad networks, and no third-party trackers**.

## 2. Data stored locally on your device

The following is stored **only on your device** in the app's private storage and local on-device database. It never leaves your phone.

| Data | Purpose | Stored where |
|------|---------|--------------|
| Aesthetic preference embedding | A mathematical vector (1280-dimensional) that summarizes the visual styles you like, used to rank wallpapers | App private database |
| Likes / dislikes / feedback | Your explicit feedback on wallpapers, used to refine recommendations | App private database |
| Applied wallpaper history | Log of wallpapers you have applied, shown in the History screen | App private database |
| Cached wallpapers | Downloaded images kept for fast wallpaper changes and offline use | App cache (`Pictures/Vanderwaals` and app cache) |

`android:allowBackup` is set to `false`, so this data is **not** backed up to Google cloud or transferred to a new device. Uninstalling the app permanently deletes all of the above.

## 3. Data you choose to upload for personalization

If you enable **Personalized Mode**, you may select one or more images from your library to teach the app your taste. These images are:

- Processed **entirely on-device** by MobileNetV4-Conv-Small to compute your preference embedding.
- **Not** uploaded to any server. The image bytes are read in memory, converted to a vector, and then discarded. Only the resulting vector is kept locally.
- Never shared, transmitted, or stored outside your device.

## 4. Network usage

The app uses the internet **only** to download publicly available wallpaper images and their catalog metadata from these public sources:

- GitHub-hosted Vanderwaals wallpaper collections
- Bing's public daily wallpaper photography archive

These requests fetch public image files only. **No personal data is sent** in any request. The app does not send your preference vector, feedback, device identifiers, or any other identifying information to these or any other servers.

## 5. Permissions the app requests

Vanderwaals requests the minimum permissions required to function. Each is listed below with its purpose.

| Permission | Why it is needed |
|-----------|------------------|
| `INTERNET` | Download public wallpapers and catalog metadata |
| `ACCESS_NETWORK_STATE` | Check connectivity before attempting downloads |
| `SET_WALLPAPER` | Apply the selected image as your device wallpaper |
| `WAKE_LOCK` | Keep the CPU awake briefly while a wallpaper change completes |
| `POST_NOTIFICATIONS` | Show a foreground-service notification while changing wallpapers (Android 13+) |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | Reliably apply scheduled wallpaper changes and the "Every Unlock" mode, even when the app is closed |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule automatic wallpaper changes after you restart your phone |
| `SCHEDULE_EXACT_ALARM` | Trigger the daily wallpaper change at your chosen exact time |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ask you (optional) to exempt the app from aggressive battery optimization so auto-change works reliably on certain devices (e.g. Samsung) |
| `WRITE_EXTERNAL_STORAGE` (maxSdkVersion 28) | Save wallpapers to `Pictures/Vanderwaals` on Android 9 and older only |

The app explicitly **removes** the following permissions that would otherwise be injected by the on-device ML library, because they are not used and not needed: `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_PHONE_STATE`.

## 6. Children's privacy

Vanderwaals is a wallpaper utility and is not directed at children under 13. The app does not knowingly collect any data from anyone, including children.

## 7. Open source

Vanderwaals is open source. You can inspect, audit, build, and verify the entire application, including this privacy policy, at the project repository:

**https://github.com/avinaxhroy/Vanderwaals**

Because the source is public, every claim in this policy can be independently verified.

## 8. Changes to this policy

If this policy changes, the updated version will be published in the repository and bundled with the corresponding app release. Continued use after an update constitutes acceptance of the revised policy.

## 9. Contact

Questions about this privacy policy or the app's data practices can be sent to:

- **Email:** hi@avinas.me
- **Issues:** https://github.com/avinaxhroy/Vanderwaals/issues
