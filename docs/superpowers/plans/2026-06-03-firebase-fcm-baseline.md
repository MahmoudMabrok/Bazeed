# Firebase FCM Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire up Firebase Cloud Messaging in the Bazeed Android app so it can receive push notifications sent from the Firebase Console.

**Architecture:** Add Firebase BoM + `firebase-messaging` and the `com.google.gms.google-services` plugin. Register a `FirebaseMessagingService` subclass that logs new tokens and renders incoming messages as system notifications on a `general` channel created at app start by a new `Application` subclass. Request `POST_NOTIFICATIONS` runtime permission on Android 13+.

**Tech Stack:** Kotlin 2.0.21, AGP 9.1.0-alpha08, Jetpack Compose, Firebase BoM 34.14.0, google-services plugin 4.4.4.

**Spec:** `docs/superpowers/specs/2026-06-03-firebase-fcm-baseline-design.md`

**Note on testing:** Baseline FCM is a thin integration over the Android framework + Firebase SDK. There are no unit tests in this plan — verification is by build success and manual smoke-test (Firebase Console → device). Each task ends with a build check.

**Note on git:** The project is not currently a git repository. The "Commit" step in each task is therefore replaced with a `git status`-equivalent sanity check (`./gradlew :app:assembleDebug`). If the user runs `git init` first, treat each commit step as a real `git add` + `git commit`.

---

### Task 1: Version catalog — add Firebase + Google Services entries

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add versions, libraries, and plugin alias**

Edit `gradle/libs.versions.toml`. The current file ends with the `[plugins]` block containing `android-application` and `kotlin-compose`. Add the new version entries to `[versions]`, new library entries to `[libraries]`, and a new plugin entry to `[plugins]`.

Resulting file:

```toml
[versions]
agp = "9.1.0-alpha08"
coreKtx = "1.18.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.10.0"
activityCompose = "1.13.0"
kotlin = "2.0.21"
composeBom = "2024.09.00"
firebaseBom = "34.14.0"
googleServices = "4.4.4"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

Note: `firebase-messaging` has no `version.ref` because the BoM controls its version.

- [ ] **Step 2: Sanity-check the TOML parses**

Run: `./gradlew help -q`
Expected: command exits with status 0. (Gradle fails fast on invalid catalogs.)

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: add firebase bom + google-services to version catalog"
```

If not a git repo, skip and continue.

---

### Task 2: Root `build.gradle.kts` — declare google-services plugin

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add the plugin alias with `apply false`**

Replace the entire root `build.gradle.kts` with:

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}
```

- [ ] **Step 2: Verify Gradle still configures**

Run: `./gradlew help -q`
Expected: exit 0, no plugin resolution errors.

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: declare google-services plugin at root"
```

---

### Task 3: `app/build.gradle.kts` — apply google-services and add Firebase dependencies

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Apply the plugin and add dependencies**

Replace the entire `app/build.gradle.kts` with:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "tools.mo3ta.bazeed"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "tools.mo3ta.bazeed"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 2: Build the debug APK to confirm google-services processing works**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. The `processDebugGoogleServices` task should run and consume `app/google-services.json` (already present, project_id `marriyapp`).

If it fails complaining about the JSON, stop and report — do not modify `google-services.json`.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: apply google-services plugin and add firebase messaging"
```

---

### Task 4: Notification icon drawable

**Files:**
- Create: `app/src/main/res/drawable/ic_notification.xml`

- [ ] **Step 1: Create the vector drawable**

Notification icons on Android 5+ must be white-on-transparent; the system tints them. Create a simple bell glyph.

`app/src/main/res/drawable/ic_notification.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFFFF">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z" />
</vector>
```

- [ ] **Step 2: Verify the resource compiles**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/ic_notification.xml
git commit -m "feat: add monochrome notification icon"
```

---

### Task 5: `BazeedApp` Application subclass — create notification channel

**Files:**
- Create: `app/src/main/java/tools/mo3ta/bazeed/BazeedApp.kt`

- [ ] **Step 1: Create the Application class**

`app/src/main/java/tools/mo3ta/bazeed/BazeedApp.kt`:

```kotlin
package tools.mo3ta.bazeed

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat

class BazeedApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createDefaultNotificationChannel()
    }

    private fun createDefaultNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_GENERAL,
            getString(R.string.notification_channel_general_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_general_description)
        }
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID_GENERAL = "general"
    }
}
```

`NotificationManagerCompat.createNotificationChannel` is a safe no-op on Android < 8 and idempotent on Android 8+, so no SDK-version guard is needed.

- [ ] **Step 2: Add the channel strings**

Replace `app/src/main/res/values/strings.xml` with:

```xml
<resources>
    <string name="app_name">Bazeed</string>
    <string name="notification_channel_general_name">General</string>
    <string name="notification_channel_general_description">General app notifications</string>
</resources>
```

- [ ] **Step 3: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/BazeedApp.kt app/src/main/res/values/strings.xml
git commit -m "feat: add application class with default notification channel"
```

---

### Task 6: `BazeedMessagingService` — token logging + message handling

**Files:**
- Create: `app/src/main/java/tools/mo3ta/bazeed/messaging/BazeedMessagingService.kt`

- [ ] **Step 1: Create the messaging service**

`app/src/main/java/tools/mo3ta/bazeed/messaging/BazeedMessagingService.kt`:

```kotlin
package tools.mo3ta.bazeed.messaging

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import tools.mo3ta.bazeed.BazeedApp
import tools.mo3ta.bazeed.MainActivity
import tools.mo3ta.bazeed.R

class BazeedMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG_TOKEN, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, BazeedApp.CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    companion object {
        private const val TAG_TOKEN = "FCM_TOKEN"
    }
}
```

Note: `NotificationManagerCompat.notify` requires `POST_NOTIFICATIONS` permission on Android 13+. If the permission was denied, the call is a no-op (logs a warning but does not throw). This is the intended behavior — the spec accepts denied notifications silently.

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/messaging/BazeedMessagingService.kt
git commit -m "feat: add FCM service for token logging and message display"
```

---

### Task 7: AndroidManifest — permission, application name, service, FCM meta-data

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Update the manifest**

Replace `app/src/main/AndroidManifest.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".BazeedApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Bazeed">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.Bazeed">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

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
    </application>

</manifest>
```

The `default_notification_channel_id` value must match `BazeedApp.CHANNEL_ID_GENERAL` (`"general"`).

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Manifest merger should not warn.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: register FCM service, notification permission, and default meta-data"
```

---

### Task 8: MainActivity — request `POST_NOTIFICATIONS` on Android 13+

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/bazeed/MainActivity.kt`

- [ ] **Step 1: Add a one-shot permission request**

Replace `app/src/main/java/tools/mo3ta/bazeed/MainActivity.kt` with:

```kotlin
package tools.mo3ta.bazeed

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import tools.mo3ta.bazeed.ui.theme.BazeedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BazeedTheme {
                NotificationPermissionGate()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result is intentionally ignored — denied means no notifications, no retry */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BazeedTheme {
        Greeting("Android")
    }
}
```

The `LaunchedEffect(Unit)` runs once per composition entry. Android remembers the user's previous answer, so on subsequent launches `checkSelfPermission` returns `GRANTED` (or the system silently skips the prompt for permanently-denied), and there is no nagging behavior.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/MainActivity.kt
git commit -m "feat: request POST_NOTIFICATIONS permission on first launch (API 33+)"
```

---

### Task 9: Final integration build + manual smoke test

**Files:** none (verification only)

- [ ] **Step 1: Clean build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. No deprecation warnings related to FCM.

- [ ] **Step 2: Install on a connected device or emulator (API 26+ recommended; API 33+ for permission test)**

Run: `./gradlew :app:installDebug`
Expected: APK installs without error.

- [ ] **Step 3: Launch the app, accept the notification permission prompt (Android 13+ only)**

Manual: tap the app icon. On Android 13+, an "Allow notifications?" dialog appears once; accept it.

- [ ] **Step 4: Capture the FCM token from Logcat**

Run (in a separate terminal):
```bash
adb logcat -s FCM_TOKEN
```
Expected: one line like `D FCM_TOKEN: <long-token-string>` shortly after first launch. Copy it.

If no token appears: kill the app and relaunch — `onNewToken` only fires when a new token is minted. Alternatively, uninstall + reinstall to force token regeneration.

- [ ] **Step 5: Send a test notification from Firebase Console**

Manual:
1. Open https://console.firebase.google.com/project/marriyapp/messaging
2. Click "New campaign" → "Notifications"
3. Title: `Test`, body: `Hello from FCM`
4. Target: "Send test message" → paste the token from Step 4 → "Test"

Expected: a notification appears in the device status bar with the bell icon, title "Test", body "Hello from FCM".

- [ ] **Step 6: Tap the notification**

Expected: the app opens to `MainActivity` showing "Hello Android!". The notification auto-dismisses.

- [ ] **Step 7: Test foreground delivery**

Manual: keep the app open and send another test notification. The notification still appears in the status bar (handled by `onMessageReceived`).

- [ ] **Step 8: Final commit (if there are tracked changes)**

```bash
git status
```

If nothing to commit, the implementation is complete.

---

## Summary checklist

- Firebase BoM + messaging dependency added via version catalog (Task 1)
- google-services plugin declared at root (Task 2) and applied in `:app` (Task 3)
- Monochrome notification icon (Task 4)
- `BazeedApp` creates the `general` channel (Task 5)
- `BazeedMessagingService` logs tokens and renders notifications (Task 6)
- Manifest registers permission, application, service, and FCM meta-data (Task 7)
- MainActivity requests `POST_NOTIFICATIONS` on Android 13+ (Task 8)
- Build + manual smoke test (Task 9)
