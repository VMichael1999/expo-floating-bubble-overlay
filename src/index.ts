import { Image } from 'react-native';

import type { BubbleOptions, NativeBubbleOptions } from './FloatingBubbleOverlay.types';
import native from './FloatingBubbleOverlayModule';

export * from './FloatingBubbleOverlay.types';

const noSubscription = { remove() {} };

/**
 * Turns a `require()` image into the URI the native side loads: the Metro dev server URL in
 * development, a bundled resource name in release builds. Other values pass through as they are.
 */
function toNative({ icon, ...rest }: BubbleOptions): NativeBubbleOptions {
  if (typeof icon !== 'number') return icon === undefined ? rest : { ...rest, icon };
  const uri = Image.resolveAssetSource(icon)?.uri;
  return uri ? { ...rest, icon: uri } : rest;
}

/**
 * Floating bubble over other apps (Android only). Tapping it brings the app to the foreground.
 * Once enabled, the bubble shows and hides by itself as the app moves between foreground and
 * background, following `showWhen`. Where it is not available, every call is a safe no-op.
 */
export const FloatingBubble = {
  /** false on iOS, web and Expo Go. */
  isAvailable: native != null,

  /** "Display over other apps" permission. */
  hasOverlayPermission: (): boolean => native?.hasOverlayPermission() ?? false,

  /** Opens the system screen to grant the permission. */
  openOverlayPermissionSettings: (): void => native?.openOverlayPermissionSettings(),

  /**
   * Turns the bubble on. It then appears and disappears by itself following `showWhen`
   * (by default, only while the app is in the background). Calling it again applies new options.
   * @returns false if not available or the permission is missing.
   */
  enable: (options: BubbleOptions = {}): boolean => native?.enable(toNative(options)) ?? false,

  /** Turns the bubble off and removes it from the screen. */
  disable: (): void => native?.disable(),

  /** Whether the bubble is turned on (it may still be hidden because of `showWhen`). */
  isEnabled: (): boolean => native?.isEnabled() ?? false,

  /**
   * Keeps the app alive in the background (foreground service) without the bubble or its
   * permission. Call it while the app is on screen. Uses `notificationTitle` / `notificationText`.
   */
  startKeepAlive: (options: BubbleOptions = {}): void =>
    native?.startKeepAlive?.(toNative(options)),

  /** Stops keeping the app alive. */
  stopKeepAlive: (): void => native?.stopKeepAlive?.(),

  /** Whether the bubble is on screen right now. */
  isVisible: (): boolean => native?.isVisible() ?? false,

  /**
   * Brings the app to the foreground from the background.
   * Needs the same permission as the bubble. @returns false if it could not.
   */
  bringAppToForeground: (): boolean => native?.bringAppToForeground() ?? false,

  /**
   * Brings the app to the foreground after N seconds using a native timer.
   * Works even while React Native is paused in the background.
   */
  scheduleBringAppToForeground: (seconds: number): void =>
    native?.scheduleBringAppToForeground?.(seconds),

  /** Cancels any pending scheduled bring-to-foreground. */
  cancelScheduledBringAppToForeground: (): void => native?.cancelScheduledBringAppToForeground?.(),

  addPressListener: (listener: () => void) =>
    native?.addListener('onPress', listener) ?? noSubscription,

  /** The user dropped the bubble on the X: it is now disabled until `enable()` is called again. */
  addDismissListener: (listener: () => void) =>
    native?.addListener('onDismiss', listener) ?? noSubscription,
};
