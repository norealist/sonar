# Sonar Project Architecture

## 1. Purpose

The Sonar project consists of three separate responsibilities:

```text
UI source
    ↓
sonar-app
    ↓
sonar-core
```

- `ui` contains the user-interface source files and frontend assets.
- `sonar-app` is the main Android application with settings, navigation, media library, playlist logic, and lifecycle management.
- `sonar-core` is the reusable Android audio library containing Kotlin host code, JNI, native playback, buffering, output negotiation, and decoders.

The dependency direction is intentionally one-way:

```text
ui → sonar-app → sonar-core
```

`sonar-core` must never depend on `sonar-app` or on the UI implementation.

## 2. Recommended Repository Layout

The recommended layout is one monorepo with separate projects/modules:

```text
repo/
├── settings.gradle.kts              Single Gradle root
├── build.gradle.kts                 Shared plugin configuration
├── gradlew
├── gradlew.bat
│
├── sonar-core/                      Reusable Android audio library
│   ├── build.gradle.kts
│   ├── src/main/kotlin/
│   ├── src/main/cpp/
│   ├── src/test/
│   ├── src/androidTest/
│   └── cpp_test/
│
├── sonar-app/                       Main Android application
│   ├── build.gradle.kts
│   ├── src/main/kotlin/
│   ├── src/main/res/
│   ├── src/main/assets/
│   └── src/main/AndroidManifest.xml
│
└── ui/                              HTML/CSS/JS source package
    ├── src/
    │   ├── html/
    │   ├── css/
    │   └── js/
    ├── package.json
    └── dist/
```

The existing `sonar-core` project currently has its own Gradle root and contains the library module plus `test-harness`. That is suitable while the core is being developed independently. Once `sonar-app` is introduced into the same build, the preferred final state is one Gradle root at `repo/`.

## 3. Module Responsibilities

### 3.1 `sonar-core`

`sonar-core` is a reusable audio library. It contains:

- `SonarPlayer` public Kotlin API.
- `NativeBridge` JNI declarations.
- `AudioTrackWrapper` and its blocking write thread.
- `OutputConfigNegotiator` and runtime output fallback.
- `SessionManager` and stable audio session ID handling.
- `StreamInfo`, `PlayerState`, `SonarError`, and `PlayerConfig` models.
- Native `PlaybackEngine` and state machine.
- Lock-free SPSC PCM ring buffer.
- Float32-to-output PCM conversion.
- Decoder factory and MP3, FLAC, WAV, OGG/Vorbis, and OGG/Opus decoders.

The core does not contain:

- UI screens or HTML/CSS/JS.
- Playlist management.
- Media catalog or library scanning.
- Artist/title/album database.
- Audio focus policy.
- `MediaSession` or notification controls.
- Network streaming.
- EQ, DVC, bass boost, or other DSP.

### 3.2 `sonar-app`

`sonar-app` is the main product application. It owns:

- Activity or Compose screens.
- ViewModels and UI state.
- File picker and URI persistence.
- Media metadata and library database.
- Playlist and current-track selection.
- Previous/next track behavior.
- Audio focus handling.
- MediaSession and notification controls.
- Headset and media-button events.
- WebView integration if the UI is HTML-based.
- User settings such as high-resolution output preference.
- Process and application lifecycle.

`sonar-app` depends on `sonar-core`, but `sonar-core` does not depend on `sonar-app`.

### 3.3 `ui`

The `ui` directory is the source package for HTML/CSS/JS. It should remain independent from native playback implementation.

There are two valid locations depending on how the UI will be used:

| UI usage | Recommended location |
|---|---|
| Only used by Sonar Android app | `sonar-app/ui/` |
| Shared by Android, web, or desktop clients | Repository-level `ui/` |

For the current project, keeping `ui` as a repository-level package is recommended because the HTML/CSS/JS files already exist and may become reusable.

The final compiled frontend output should be copied or built into:

```text
sonar-app/src/main/assets/ui/
```

Source files should not be duplicated manually inside Android assets. The application build should own this copy/build step.

## 4. Gradle Integration

### 4.1 Single Gradle Root

The future top-level `repo/settings.gradle.kts` should include both Android modules:

```kotlin
rootProject.name = "sonar"

include(":sonar-core")
include(":sonar-app")
```

If the current directory layout is preserved temporarily, explicit project paths can be used:

```kotlin
include(":sonar-core")
project(":sonar-core").projectDir = file("sonar-core/sonar-core")

include(":sonar-app")
project(":sonar-app").projectDir = file("sonar-app")
```

The final project should have one authoritative Gradle root. Avoid maintaining two competing `settings.gradle.kts` files for the same application build.

### 4.2 Application Dependency

`sonar-app/build.gradle.kts` should depend directly on the library module during development:

```kotlin
dependencies {
    implementation(project(":sonar-core"))
}
```

This allows changes to Kotlin, JNI, or C++ code in `sonar-core` to be tested immediately without manually rebuilding and copying an AAR.

The application should use the same compatible toolchain as the core:

```kotlin
android {
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        targetSdk = 37
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
}
```

### 4.3 AAR Distribution

When the core is consumed by an independent application or release pipeline, it can be distributed as an AAR:

```text
sonar-app/app/libs/sonar-core-release.aar
```

```kotlin
dependencies {
    implementation(files("libs/sonar-core-release.aar"))
}
```

The AAR contains the Kotlin library classes and native `libsonar_core.so` binaries. The application does not need to compile the native sources when consuming the AAR.

Source-module dependency is preferred during active development. AAR or Maven publication is preferred for versioned release integration.

## 5. UI to Player Integration

The UI must communicate with an application-level controller or ViewModel, not directly with JNI:

```text
HTML/CSS/JS or Android UI
             ↕
      UI bridge / ViewModel
             ↕
      AppPlayerController
             ↕
         SonarPlayer
             ↕
         sonar-core
```

### 5.1 Application Controller

The application should wrap `SonarPlayer` with a controller that exposes UI-oriented operations:

```kotlin
class AppPlayerController(context: Context) {
    private val player = SonarPlayer(context)

    fun open(uri: Uri): SonarError = player.open(uri)

    fun togglePlayPause(): SonarError {
        return when (player.state) {
            PlayerState.PLAYING,
            PlayerState.BUFFERING -> player.pause()

            PlayerState.PAUSED -> player.resume()

            else -> player.play()
        }
    }

    fun stop(): SonarError = player.stop()

    fun seekTo(positionMs: Long): SonarError =
        player.seekTo(positionMs)

    fun release() = player.release()
}
```

The application controller is the correct place to combine playback with playlist selection, UI state, metadata, and audio focus.

### 5.2 ViewModel Ownership

`SonarPlayer` should normally live inside a `ViewModel`, not directly inside an `Activity`. This keeps playback alive across configuration changes:

```text
Activity / Compose screen
            ↓
       PlayerViewModel
            ↓
        SonarPlayer
```

The `ViewModel` should expose a UI state containing:

- `PlayerState`.
- Current `StreamInfo`.
- Current position and duration.
- Output description.
- Audio session ID.
- Current error.
- Current artist/title/album from application metadata.

The current core exposes state as properties rather than `Flow` callbacks. The application can poll state every 100–250 ms from a coroutine and publish it as `StateFlow`.

## 6. HTML/WebView Integration

If the existing HTML/CSS/JS UI is hosted in a WebView, the bridge belongs to `sonar-app`:

```text
JavaScript
    ↕ @JavascriptInterface / evaluateJavascript
WebPlayerBridge
    ↕
AppPlayerController
    ↕
SonarPlayer
```

Example bridge shape:

```kotlin
class WebPlayerBridge(
    private val controller: AppPlayerController,
) {
    @JavascriptInterface
    fun playPause() {
        controller.togglePlayPause()
    }

    @JavascriptInterface
    fun stop() {
        controller.stop()
    }
}
```

The app sends state changes back to JavaScript through a small, explicit event contract:

```kotlin
webView.evaluateJavascript(
    "window.onPlayerStateChanged(${state.name.jsonQuote()})",
    null,
)
```

JavaScript must not call `NativeBridge` directly. This keeps the WebView replaceable and prevents UI code from depending on JNI implementation details.

## 7. File Selection and URI Handling

The app should use `ACTION_OPEN_DOCUMENT`:

```kotlin
val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    .addCategory(Intent.CATEGORY_OPENABLE)
    .setType("audio/*")
```

For a long-lived media library, the app should persist URI permission:

```kotlin
val flags = data.flags and (
    Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    )

contentResolver.takePersistableUriPermission(uri, flags)
```

`SonarPlayer.open(uri)` opens a `ParcelFileDescriptor` for `content://` URIs and streams audio directly through `/proc/self/fd/<fd>` without copying data into the application cache. The app remains responsible for storing the original URI, display name, metadata, and playlist record.

## 8. Metadata and Media Library

`StreamInfo` contains technical stream information only:

```kotlin
info.sampleRate
info.channels
info.durationMs
info.sourceBitDepth
info.codec
```

Artist, title, album, artwork, genre, and library identity belong to `sonar-app`. They should be obtained through `MediaMetadataRetriever`, `MediaStore`, or the app's own metadata database.

The core should never be expanded into a media catalog merely to expose UI metadata.

## 9. Playlist and Previous/Next

The core has no playlist state. The app owns:

- Track collection.
- Current index.
- Sorting and filtering.
- Previous/next selection.
- Repeat and shuffle.

The core methods receive the URI selected by the app:

```kotlin
player.next(nextUri)
player.previous(previousUri)
```

This allows the same core to serve a single-track test harness, a local library, or a future remote playlist controller.

## 10. Audio Focus, MediaSession, and External Effects

`sonar-core` intentionally does not manage audio focus or `MediaSession`. `sonar-app` should implement:

- Audio focus request and abandonment.
- Pause on permanent focus loss.
- Temporary duck/pause behavior according to product requirements.
- Media notification actions.
- Headset and hardware media buttons.
- Android Auto or external transport controls.

When focus is lost:

```kotlin
player.pause()
```

When playback is allowed again:

```kotlin
player.resume()
```

The core exposes a stable session ID:

```kotlin
val sessionId = player.audioSessionId
```

The app or external effect layer can use this ID for EQ/DVC integration. The core itself does not implement EQ, DVC, bass boost, reverb, or other DSP.

## 11. Output Format Settings

The application owns the user preference:

```kotlin
player.setHighResolutionOutputEnabled(true)
```

The core owns capability probing and runtime fallback. The app displays the actual selected output:

```kotlin
player.outputDescription
player.outputBitDepth
```

Possible result values include:

```text
16-bit PCM
24-bit packed PCM
32-bit PCM
32-bit float PCM
```

The requested format is not a guarantee of bit-perfect DAC output. AudioFlinger, the device HAL, USB/Bluetooth route, and EQ/DVC may convert the stream. The application should describe the setting as best-effort rather than as guaranteed hardware bit depth.

## 12. Lifecycle Rules

### Activity recreation

Keep `SonarPlayer` in a `ViewModel`. Do not release it on every Activity recreation.

### Leaving the application

When the application deliberately ends playback:

```kotlin
player.stop()
```

When the owner is permanently destroyed:

```kotlin
player.release()
```

### Opening another track

`SonarPlayer.open()` releases the previous `AudioTrack`, stops the old native decoder, opens the new file, negotiates output, and creates a new track while keeping the same audio session ID.

## 13. Migration Plan

The current `sonar-core` project can be integrated incrementally:

1. Create `repo/sonar-app` as the main Android application.
2. Keep `sonar-core` as a separate library module.
3. Move the existing `ui` source package under `repo/ui` if it is shared, or under `sonar-app/ui` if it is app-specific.
4. Add a top-level `repo/settings.gradle.kts` including `:sonar-core` and `:sonar-app`.
5. Point `:sonar-core` to the existing library module directory.
6. Add `implementation(project(":sonar-core"))` to `sonar-app`.
7. Create `PlayerViewModel` and `AppPlayerController` in `sonar-app`.
8. Connect file picker and persisted URI storage.
9. Connect HTML/JS through a WebView bridge if applicable.
10. Add metadata, playlist, audio focus, and MediaSession at the app layer.
11. Build and validate the app with MP3, FLAC, WAV, OGG/Vorbis, and OGG/Opus fixtures.
12. Publish `sonar-core` as a versioned AAR or Maven artifact when the app/core boundary is stable.

## 14. Decision Summary

The project should be organized as:

```text
One repository
├── sonar-core   reusable audio library
├── sonar-app    main Android application
└── ui           frontend source package
```

The folders remain physically separate, but the projects are developed together in one monorepo. This preserves a clean reusable core while allowing `sonar-app` and the UI to evolve independently.
