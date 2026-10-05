import { AndroidConfig, type ConfigPlugin, withAndroidManifest } from 'expo/config-plugins';

export type FloatingBubbleOverlayPluginProps = {
  /**
   * Why your app needs the `specialUse` foreground service. Android 14+ requires it and
   * Google Play reviews it. Defaults to a generic description shipped with the package.
   */
  specialUseDescription?: string;
};

type AndroidManifest = AndroidConfig.Manifest.AndroidManifest;

type ManifestProperty = {
  $: { 'android:name': string; 'android:value': string; 'tools:replace'?: string };
};
type ManifestService = { $: Record<string, string | undefined>; property?: ManifestProperty[] };

export const SERVICE_NAME = 'expo.modules.floatingbubbleoverlay.service.KeepAliveService';
export const SPECIAL_USE_PROPERTY = 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE';

/**
 * Overrides the `specialUse` description of the package's service in the app manifest.
 * The package manifest declares a default; `tools:replace` makes the app value win on merge.
 * Idempotent: running it again updates the same entry.
 */
export function setSpecialUseDescription(
  manifest: AndroidManifest,
  description: string | undefined
): AndroidManifest {
  const value = description?.trim();
  if (!value) return manifest;

  AndroidConfig.Manifest.ensureToolsAvailable(manifest);
  const application = AndroidConfig.Manifest.getMainApplicationOrThrow(manifest) as {
    service?: ManifestService[];
  };
  application.service ??= [];

  let service = application.service.find((s) => s.$['android:name'] === SERVICE_NAME);
  if (!service) {
    service = { $: { 'android:name': SERVICE_NAME, 'tools:node': 'merge' } };
    application.service.push(service);
  }

  service.property = (service.property ?? []).filter(
    (p) => p.$['android:name'] !== SPECIAL_USE_PROPERTY
  );
  service.property.push({
    $: {
      'android:name': SPECIAL_USE_PROPERTY,
      'android:value': value,
      'tools:replace': 'android:value',
    },
  });
  return manifest;
}

const withFloatingBubbleOverlay: ConfigPlugin<FloatingBubbleOverlayPluginProps | void> = (
  config,
  props
) =>
  withAndroidManifest(config, (c) => {
    c.modResults = setSpecialUseDescription(c.modResults, props?.specialUseDescription);
    return c;
  });

export default withFloatingBubbleOverlay;
