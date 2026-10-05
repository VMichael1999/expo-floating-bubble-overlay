import { registerWebModule, NativeModule } from 'expo';

import { FloatingBubbleOverlayModuleEvents } from './FloatingBubbleOverlay.types';

// FloatingBubbleOverlayModule is not available on the web platform.
class FloatingBubbleOverlayModule extends NativeModule<FloatingBubbleOverlayModuleEvents> {}

export default registerWebModule(FloatingBubbleOverlayModule, 'FloatingBubbleOverlayModule');
