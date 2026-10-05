import type { OpcionesBurbuja } from './FloatingBubbleOverlay.types';
import nativo from './FloatingBubbleOverlayModule';

export * from './FloatingBubbleOverlay.types';

const sinSuscripcion = { remove() {} };

/**
 * Burbuja flotante sobre otras apps (solo Android). Tocarla trae la app al frente.
 * Donde no está disponible, todo es un no-op seguro.
 */
export const BurbujaFlotante = {
  /** false en iOS, web y Expo Go. */
  disponible: nativo != null,

  /** Permiso "Mostrar sobre otras apps". */
  tienePermiso: (): boolean => nativo?.tienePermiso() ?? false,

  /** Abre la pantalla del sistema para conceder el permiso. */
  abrirAjustesPermiso: (): void => nativo?.abrirAjustesPermiso(),

  /** @returns false si no está disponible o falta el permiso. */
  mostrar: (opciones: OpcionesBurbuja = {}): boolean => nativo?.mostrar(opciones) ?? false,

  ocultar: (): void => nativo?.ocultar(),

  /**
   * Mantiene la app viva en segundo plano (servicio en primer plano) sin burbuja ni permiso.
   * Llamar con la app en pantalla. Usa `tituloNotificacion` / `textoNotificacion`.
   */
  mantenerActiva: (opciones: OpcionesBurbuja = {}): void => nativo?.mantenerActiva?.(opciones),

  /** Deja de mantenerla viva. */
  soltarActiva: (): void => nativo?.soltarActiva?.(),

  estaVisible: (): boolean => nativo?.estaVisible() ?? false,

  /**
   * Trae la app al frente desde segundo plano.
   * Requiere el mismo permiso que la burbuja. @returns false si no se pudo.
   */
  abrirApp: (): boolean => nativo?.abrirApp() ?? false,

  /**
   * Programa la apertura automática de la app tras N segundos usando un temporizador nativo.
   * Funciona aunque React Native esté pausado en segundo plano.
   */
  programarApertura: (segundos: number): void => nativo?.programarApertura?.(segundos),

  /** Cancela cualquier apertura diferida programada previamente. */
  cancelarApertura: (): void => nativo?.cancelarApertura?.(),

  alTocar: (cb: () => void) => nativo?.addListener('onTocar', cb) ?? sinSuscripcion,

  alCerrar: (cb: () => void) => nativo?.addListener('onCerrar', cb) ?? sinSuscripcion,
};
