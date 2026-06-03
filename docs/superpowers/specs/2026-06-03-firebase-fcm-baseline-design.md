# Firebase FCM Baseline — Design

**Date:** 2026-06-03
**Module:** `app` (`tools.mo3ta.bazeed`)
**Firebase project:** `marriyapp` (config already in `app/google-services.json`)

## Goal

Wire up Firebase Cloud Messaging so the app can receive push notifications sent from the Firebase Console. No backend integration, no topic subscriptions, no deep linking. Token surfaced via Logcat only.

## Scope

### In scope
- Gradle setup for Firebase BoM + `firebase-messaging` + Google Services plugin.
- `FirebaseMessagingService` subclass that logs new tokens and renders incoming messages as system notifications.
- Default notification channel (`general`, IMPORTANCE_DEFAULT) created at app start.
- Monochrome notification icon (`ic_notification`).
- `POST_NOTIFICATIONS` runtime permission for Android 13+.
- Manifest declarations: permission, application class, messaging service, default-icon and default-channel-id meta-data.

### Out of scope
- Backend token upload.
- Topic subscriptions (`subscribeToTopic`).
- Deep-link routing from notification tap (notifications open `MainActivity` only).
- On-screen token display.
- Custom notification sounds / badges / large icons.
- Notification grouping or per-category channels.

## Architecture

### Components

**`BazeedApp` (Application subclass)**
- Single responsibility: ensure the default notification channel exists before any notification is posted.
- Creates the `general` channel in `onCreate` via `NotificationManagerCompat.createNotificationChannel`. Idempotent — safe to call on every launch.

**`BazeedMessagingService` (FirebaseMessagingService subclass)**
- `onNewToken(token: String)`: `Log.d("FCM_TOKEN", token)`. No persistence, no network call.
- `onMessageReceived(message: RemoteMessage)`: builds a `NotificationCompat.Builder` on the `general` channel using:
  - Title: `message.notification?.title` if present, else `message.data["title"]`, else a fallback ("Bazeed").
  - Body: `message.notification?.body` if present, else `message.data["body"]`, else empty.
  - Content intent: `PendingIntent` that opens `MainActivity` with `FLAG_ACTIVITY_CLEAR_TOP`.
  - Icon: `R.drawable.ic_notification`.
- Posts via `NotificationManagerCompat.notify` using a unique ID (`System.currentTimeMillis().toInt()` is sufficient for baseline).

This design intentionally handles **both** notification-only payloads (FCM displays automatically when app is in background, but we still want a custom icon/channel in foreground) **and** data payloads (we display manually in either state). When `notification` is present and app is backgrounded, FCM will deliver via the system tray automatically — `onMessageReceived` only fires for foreground notification-payloads and for all data-payloads.

**`ic_notification.xml` (vector drawable)**
- White silhouette on transparent background (Android 5+ requirement; the system tints/masks it). A simple bell or app-initial glyph at 24dp.

### Files

New:
- `app/src/main/java/tools/mo3ta/bazeed/BazeedApp.kt`
- `app/src/main/java/tools/mo3ta/bazeed/messaging/BazeedMessagingService.kt`
- `app/src/main/res/drawable/ic_notification.xml`

Modified:
- `gradle/libs.versions.toml` — add Firebase BoM coord, messaging coord, google-services plugin coord + versions.
- `build.gradle.kts` (root) — add `alias(libs.plugins.google.services) apply false`.
- `app/build.gradle.kts` — apply `libs.plugins.google.services`; add `implementation(platform(libs.firebase.bom))` and `implementation(libs.firebase.messaging)`.
- `app/src/main/AndroidManifest.xml` — see below.
- `app/src/main/java/tools/mo3ta/bazeed/MainActivity.kt` — request `POST_NOTIFICATIONS` on first launch via `rememberLauncherForActivityResult` (no UI surface).

### AndroidManifest changes

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<application
    android:name=".BazeedApp"
    ... existing attrs ... >

    <service
        android:name=".messaging.BazeedMessagingService"
        android:exported="false">
        <intent-filter>
            <action android:name="com.google.firebase.MESSAGING_EVENT" />
        </intent-filter>
    </service>

    <meta-data
        android:name="com.google.firebase.messaging.default_notification_icon"
        android:resource="@drawable/ic_notification" />
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_channel_id"
        android:value="general" />

    <activity ... />
</application>
```

### Versions

Pinning to current stable:
- `firebaseBom = "34.14.0"` (BoM transitively pins `firebase-messaging`)
- `googleServices = "4.4.4"`

Both compatible with AGP 9.1.0-alpha08 and Kotlin 2.0.21. No KSP/Hilt added.

## Data flow

```
Firebase Console (test send)
    │
    ▼
FCM backend ──── push ───▶ device
    │
    ▼
BazeedMessagingService.onMessageReceived
    │
    ▼
NotificationCompat.Builder (channel="general", icon=ic_notification)
    │
    ▼
NotificationManagerCompat.notify(id, notification)
    │
    ▼
System tray ── tap ──▶ MainActivity (CLEAR_TOP)
```

Token flow:
```
FCM SDK ──▶ BazeedMessagingService.onNewToken(token)
                │
                ▼
            Log.d("FCM_TOKEN", token)   // developer copies from Logcat
```

## Error handling

- **Permission denied (Android 13+):** the launcher result is ignored. Notifications simply don't display; no crash, no retry, no nag.
- **Channel creation:** `NotificationManagerCompat.createNotificationChannel` is no-op on Android < 8 and idempotent everywhere else.
- **Missing `google-services.json`:** build fails at the `processGoogleServices` task — surfaced by Gradle, no in-app handling needed. File is already present.
- **Notification post when channel missing:** cannot happen — `BazeedApp.onCreate` runs before any service-driven notification.

## Testing

Manual verification only (no unit tests for baseline FCM — the integration surface is the Android framework + Firebase SDK):

1. Build and install. Confirm Logcat shows `FCM_TOKEN: <token>` on first launch.
2. Send a test notification from Firebase Console → Messaging using the token. App backgrounded: notification appears in tray with `ic_notification`. Tap → opens `MainActivity`.
3. Send while app is foregrounded: notification still appears (handled in `onMessageReceived`).
4. On an Android 13+ device, fresh install: confirm the permission prompt appears once on first launch and is not re-prompted on subsequent launches.
5. On an Android < 13 device: confirm notifications display without any permission prompt.

## Open assumptions

- The Firebase project `marriyapp` already has FCM enabled (default for new Firebase projects).
- `ic_notification` will be a placeholder glyph; user can swap it for a branded asset later without touching code.
