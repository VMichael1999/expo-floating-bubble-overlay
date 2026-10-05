import type { BubbleOptions } from './FloatingBubbleOverlay.types';
import native from './FloatingBubbleOverlayModule';

export * from './FloatingBubbleOverlay.types';

const noSubscription = { remove() {} };

/**
 * Floating bubble over other apps (Android only). Tapping it brings the app to the foreground.
 * Where it is not available, every call is a safe no-op.
 */
export const FloatingBubble = {
  /** false on iOS, web and Expo Go. */
  isAvailable: native != null,

  /** "Display over other apps" permission. */
  hasOverlayPermission: (): boolean => native?.hasOverlayPermission() ?? false,

  /** Opens the system screen to grant the permission. */
  openOverlayPermissionSettings: (): void => native?.openOverlayPermissionSettings(),

  /** @returns false if not available or the permission is missing. */
  show: (options: BubbleOptions = {}): boolean => native?.show(options) ?? false,

  hide: (): void => native?.hide(),

  /**
   * Keeps the app alive in the background (foreground service) without the bubble or its
   * permission. Call it while the app is on screen. Uses `notificationTitle` / `notificationText`.
   */
  startKeepAlive: (options: BubbleOptions = {}): void => native?.startKeepAlive?.(options),

  /** Stops keeping the app alive. */
  stopKeepAlive: (): void => native?.stopKeepAlive?.(),

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

  addDismissListener: (listener: () => void) =>
    native?.addListener('onDismiss', listener) ?? noSubscription,
};
