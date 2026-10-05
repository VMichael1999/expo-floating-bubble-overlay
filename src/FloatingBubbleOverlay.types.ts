/** Opciones de la burbuja (todas opcionales). Tamaños y posiciones en dp. */
export interface OpcionesBurbuja {
  /** Diámetro, de 40 a 96. Por defecto 60. */
  tamano?: number;
  /** De 0.2 a 1. Por defecto 1. */
  opacidad?: number;
  /** Nombre de un drawable/mipmap nativo; por defecto el ícono de la app. */
  icono?: string;
  /** Qué tan cerca de la X hay que soltarla para cerrarla. Por defecto 96. */
  distanciaCerrar?: number;
  /** Al soltarla se pega al borde lateral más cercano. Por defecto true. */
  pegarAlBorde?: boolean;
  /** Posición inicial; por defecto borde derecho, a un tercio de la altura. */
  x?: number;
  y?: number;
  /** Notificación del servicio que mantiene viva la app mientras se ve la burbuja. */
  tituloNotificacion?: string;
  textoNotificacion?: string;
}

export type Eventos = {
  /** Se tocó la burbuja: la app ya se está abriendo y la burbuja se ocultó. */
  onTocar: () => void;
  /** El usuario la arrastró a la X. */
  onCerrar: () => void;
};
