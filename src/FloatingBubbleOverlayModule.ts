import { NativeModule, requireNativeModule } from 'expo';

import { FloatingBubbleOverlayModuleEvents } from './FloatingBubbleOverlay.types';

declare class FloatingBubbleOverlayModule extends NativeModule<FloatingBubbleOverlayModuleEvents> {
  hello(): string;
}

export default requireNativeModule<FloatingBubbleOverlayModule>('FloatingBubbleOverlay');
