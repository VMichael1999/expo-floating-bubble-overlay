import { readFileSync } from 'fs';
import { join } from 'path';

import { SERVICE_NAME, SPECIAL_USE_PROPERTY } from '../index';

/** The package's own Android manifest, which apps merge into theirs. */
const manifest = readFileSync(
  join(__dirname, '../../../android/src/main/AndroidManifest.xml'),
  'utf8'
);

describe('package AndroidManifest.xml', () => {
  it('declares the service the plugin targets', () => {
    expect(manifest).toContain(`android:name="${SERVICE_NAME}"`);
  });

  it('ships a generic English specialUse description', () => {
    const property = new RegExp(
      `android:name="${SPECIAL_USE_PROPERTY.replace(/\./g, '\\.')}"\\s+android:value="([^"]*)"`
    );

    expect(manifest.match(property)?.[1]).toBe(
      'Keeps the app running in the background and shows a floating shortcut to return to it from other apps'
    );
  });
});
