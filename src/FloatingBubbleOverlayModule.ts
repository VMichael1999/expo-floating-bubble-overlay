import { NativeModule, requireOptionalNativeModule } from 'expo';
import { Platform } from 'react-native';

import type { FloatingBubbleEvents, NativeBubbleOptions } from './FloatingBubbleOverlay.types';

declare class FloatingBubbleOverlayModule extends NativeModule<FloatingBubbleEvents> {
  hasOverlayPermission(): boolean;
  openOverlayPermissionSettings(): void;
  enable(options: NativeBubbleOptions): boolean;
  startKeepAlive(options: NativeBubbleOptions): void;
  stopKeepAlive(): void;
  disable(): void;
  bringAppToForeground(): boolean;
  scheduleBringAppToForeground(seconds: number): void;
  cancelScheduledBringAppToForeground(): void;
  isEnabled(): boolean;
  isVisible(): boolean;
}

// Only exists on Android with a development build; null on iOS, web and Expo Go.
export default Platform.OS === 'android'
  ? requireOptionalNativeModule<FloatingBubbleOverlayModule>('FloatingBubbleOverlay')
  : null;
