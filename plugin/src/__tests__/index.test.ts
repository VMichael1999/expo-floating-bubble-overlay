import { AndroidConfig } from 'expo/config-plugins';

import { SERVICE_NAME, SPECIAL_USE_PROPERTY, setSpecialUseDescription } from '../index';

type AndroidManifest = AndroidConfig.Manifest.AndroidManifest;

function baseManifest(): AndroidManifest {
  return {
    manifest: {
      $: { 'xmlns:android': 'http://schemas.android.com/apk/res/android' },
      application: [{ $: { 'android:name': '.MainApplication' } }],
    },
  };
}

function services(manifest: AndroidManifest): any[] {
  return (AndroidConfig.Manifest.getMainApplicationOrThrow(manifest) as any).service ?? [];
}

describe('setSpecialUseDescription', () => {
  it('adds the service with a property that replaces the package value', () => {
    const m = setSpecialUseDescription(baseManifest(), 'Keeps the app running for trips');

    expect(m.manifest.$['xmlns:tools']).toBe('http://schemas.android.com/tools');
    expect(services(m)).toEqual([
      {
        $: { 'android:name': SERVICE_NAME, 'tools:node': 'merge' },
        property: [
          {
            $: {
              'android:name': SPECIAL_USE_PROPERTY,
              'android:value': 'Keeps the app running for trips',
              'tools:replace': 'android:value',
            },
          },
        ],
      },
    ]);
  });

  it('is idempotent: running it again updates the same entry', () => {
    const m = setSpecialUseDescription(baseManifest(), 'First');
    setSpecialUseDescription(m, 'Second');

    expect(services(m)).toHaveLength(1);
    expect(services(m)[0].property).toHaveLength(1);
    expect(services(m)[0].property[0].$['android:value']).toBe('Second');
  });

  it('keeps other services and properties untouched', () => {
    const m = baseManifest();
    const other = { $: { 'android:name': 'com.example.OtherService' } };
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(m) as any;
    app.service = [
      other,
      {
        $: { 'android:name': SERVICE_NAME },
        property: [{ $: { 'android:name': 'some.other.property', 'android:value': 'x' } }],
      },
    ];

    setSpecialUseDescription(m, 'Description');

    expect(services(m)[0]).toBe(other);
    expect(services(m)[1].property.map((p: any) => p.$['android:name'])).toEqual([
      'some.other.property',
      SPECIAL_USE_PROPERTY,
    ]);
  });

  it.each([undefined, '', '   '])('does not touch the manifest without a description (%p)', (d) => {
    const m = setSpecialUseDescription(baseManifest(), d);

    expect(m).toEqual(baseManifest());
  });

  it('trims the description', () => {
    const m = setSpecialUseDescription(baseManifest(), '  Trimmed  ');

    expect(services(m)[0].property[0].$['android:value']).toBe('Trimmed');
  });
});
