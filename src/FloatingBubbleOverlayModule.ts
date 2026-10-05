import { NativeModule, requireOptionalNativeModule } from 'expo';
import { Platform } from 'react-native';

import type { BubbleOptions, FloatingBubbleEvents } from './FloatingBubbleOverlay.types';

declare class FloatingBubbleOverlayModule extends NativeModule<FloatingBubbleEvents> {
  hasOverlayPermission(): boolean;
  openOverlayPermissionSettings(): void;
  show(options: BubbleOptions): boolean;
  startKeepAlive(options: BubbleOptions): void;
  stopKeepAlive(): void;
  hide(): void;
  bringAppToForeground(): boolean;
  scheduleBringAppToForeground(seconds: number): void;
  cancelScheduledBringAppToForeground(): void;
  isVisible(): boolean;
}

// Only exists on Android with a development build; null on iOS, web and Expo Go.
export default Platform.OS === 'android'
  ? requireOptionalNativeModule<FloatingBubbleOverlayModule>('FloatingBubbleOverlay')
  : null;
