import { NativeModule, requireOptionalNativeModule } from 'expo';
import { Platform } from 'react-native';

import type { Eventos, OpcionesBurbuja } from './FloatingBubbleOverlay.types';

declare class FloatingBubbleOverlayModule extends NativeModule<Eventos> {
  tienePermiso(): boolean;
  abrirAjustesPermiso(): void;
  mostrar(opciones: OpcionesBurbuja): boolean;
  mantenerActiva(opciones: OpcionesBurbuja): void;
  soltarActiva(): void;
  ocultar(): void;
  abrirApp(): boolean;
  programarApertura(segundos: number): void;
  cancelarApertura(): void;
  estaVisible(): boolean;
}

// Solo existe en Android con un development build; en iOS, web y Expo Go es null.
export default Platform.OS === 'android'
  ? requireOptionalNativeModule<FloatingBubbleOverlayModule>('FloatingBubbleOverlay')
  : null;
