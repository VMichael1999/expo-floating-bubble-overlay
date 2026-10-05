/** Bubble options (all optional). Sizes and positions in dp. */
export interface BubbleOptions {
  /** Diameter, from 40 to 96. Defaults to 60. */
  size?: number;
  /** From 0.2 to 1. Defaults to 1. */
  opacity?: number;
  /** Name of a native drawable/mipmap; defaults to the app icon. */
  icon?: string;
  /** How close to the X the bubble must be released to dismiss it. Defaults to 96. */
  dismissDistance?: number;
  /** On release the bubble snaps to the nearest side edge. Defaults to true. */
  snapToEdge?: boolean;
  /** Initial position; defaults to the right edge, a third of the way down. */
  x?: number;
  y?: number;
  /** Notification of the foreground service that keeps the app alive while the bubble shows. */
  notificationTitle?: string;
  notificationText?: string;
}

export type FloatingBubbleEvents = {
  /** The bubble was tapped: the app is already coming to the foreground and the bubble is hidden. */
  onPress: () => void;
  /** The user dragged the bubble onto the X. */
  onDismiss: () => void;
};
