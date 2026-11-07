# Android demo

widget-sandbox's corpus in a chat-like feed on Android, each widget in a bubble, rendered by TwidgetCore.
It is set up the way Telegram for Android is, so its pieces can move into `TMessagesProj` as they are:
Java 8 source with no Kotlin and no AndroidX, views built in code (`AndroidUtilities.dp`, `LayoutHelper`,
a small `Theme`), and one native library built by CMake through `externalNativeBuild`.

| Piece | Here | In Telegram it would go |
|---|---|---|
| Widget JS, validator, host | widget-sandbox's C++ `WidgetHost` (`packages/desktop`), behind `cpp/widget_host_jni.cpp` and `WidgetHostNative.java` | `TMessagesProj/jni`, next to tgnet |
| DOM, CSS, layout, events, animations, canvas | TwidgetCore, behind `platform/android/jni.cpp` and `LitehtmlNative.java` | `TMessagesProj/jni` |
| Fonts, text widths, images, canvas bitmaps, painting | `LitehtmlBackend`, `DisplayListPainter`, `CanvasBitmaps` (Java copies of the Kotlin in `platform/android`) | `org.telegram.ui.Components` |
| Driving the core, compositor layers, text editing | `WidgetRenderer`, `WidgetView` | `org.telegram.ui.Components` |
| The feed | `LaunchActivity`, `WidgetCell` | a `ChatMessageCell` for widget messages |

## Build

Needs the Android SDK with NDK 27 and CMake 3.22, and widget-sandbox checked out next to twidget (or its
path in `TWIDGET_WIDGET_SANDBOX`). The Gradle wrapper is not checked in: open the folder in Android Studio,
or run `gradle wrapper` once with Gradle 8.9 or later, then

```sh
./gradlew :app:installDebug
```

`app/build.gradle` copies the corpus and the WidgetJS bundles into the assets; the app copies the bundles
out to its files directory on start, where the host reads them.

The core is C++20: Telegram's own native build still pins NDK 21, which is too old for it.

## How it fits together

- **Threads.** The core and its Java backend run on the main thread: the backend's callbacks (fonts,
  text widths, images) are Java, called from the core. WidgetHost runs on threads of its own and calls
  the renderer there; `WidgetRenderer.bridge()` posts each call to the main thread, and the core's
  answers go back through `WidgetHostNative.rendererMessage`. The host's threads attach to the VM for
  their lifetime.
- **Painting.** A widget's document is recorded into a display list once per vsync at most and painted
  into a bitmap the view keeps, only where the core reports damage. Transform and opacity animations,
  and elements a script moves, are compositor layers: rastered once, drawn at their tracks' matrices
  each frame while the core sleeps. A layer inside another's moves with it.
- **Text editing.** A tap on a text input or textarea puts an `EditText` over it, from the core's
  `editor()`: it brings the keyboard and the input method, typing reaches the widget as input events,
  and losing the focus ends the editing with change and blur.
- **Scrolling.** A widget gets the first 150 ms of a touch; after that the feed may take it over.
  Widgets scrolled out of sight pause and let go of their caches.

## State

Written on Windows without an Android toolchain. The Java type-checks against `android.jar` (API 35)
at Java 8, and the JNI bridge against the JDK's `jni.h`, but nothing here has been built with Gradle or
the NDK, or run on a device. Things to check first:

- the CMake build of widget-sandbox's desktop package for Android (it has only been built for desktops;
  it takes its random bytes from `arc4random_buf` on Android since widget-sandbox d4dd581);
- `LitehtmlBackend`'s font metrics against what litehtml lays out with;
- the gesture split between a widget and the feed.
