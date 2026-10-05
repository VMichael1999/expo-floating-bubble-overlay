# Changelog

## 0.1.0 (2026-10-05)

First release of `expo-floating-bubble-overlay`, extracted from the floating bubble built for the Run Pilot app and turned into a generic package.

### 🎉 New features

- Floating bubble over other apps on Android, with tap to return to the app, drag, snap to edge and drop on the ✕ to dismiss.
- `enable()` / `disable()` / `isEnabled()` / `isVisible()`: turn the bubble on and off at will.
- `showWhen` option: show the bubble only in the background (default), only in the foreground, or always. The package follows the app state natively.
- `hideOnPress` option: by default a tap opens the app and hides the bubble until the app next goes to the background; `false` keeps it on screen.
- `icon` option: the app icon by default, or any image from `require()`, an https URL, a file, base64 or a native resource. Falls back to the app icon if the image cannot be loaded.
- Options for `size`, `opacity`, `x` / `y`, `snapToEdge`, `dismissDistance`, `notificationTitle` and `notificationText`.
- `onPress` and `onDismiss` events (`addPressListener`, `addDismissListener`).
- Foreground service while the bubble is visible, plus `startKeepAlive()` / `stopKeepAlive()` to keep the app alive without the bubble.
- `bringAppToForeground()`, `scheduleBringAppToForeground(seconds)` and `cancelScheduledBringAppToForeground()`.
- Config plugin with the `specialUseDescription` option for the Android 14+ foreground service declaration.
- Safe no-op on iOS, web and Expo Go (`isAvailable` is `false`).

### 🐛 Bug fixes

- The service notification now updates when its texts change while the service is already running or still starting.

### 💡 Others

- English API, default texts and documentation.
