import { Platform } from 'react-native';

type Facade = typeof import('../index').FloatingBubble;

function fakeNative() {
  return {
    hasOverlayPermission: jest.fn(() => true),
    openOverlayPermissionSettings: jest.fn(),
    enable: jest.fn(() => true),
    startKeepAlive: jest.fn(),
    stopKeepAlive: jest.fn(),
    disable: jest.fn(),
    bringAppToForeground: jest.fn(() => true),
    scheduleBringAppToForeground: jest.fn(),
    cancelScheduledBringAppToForeground: jest.fn(),
    isEnabled: jest.fn(() => true),
    isVisible: jest.fn(() => true),
    addListener: jest.fn(() => ({ remove: jest.fn() })),
  };
}

/** Loads the facade with the given platform, native module and `require()` asset resolution. */
function load(
  os: typeof Platform.OS,
  native: ReturnType<typeof fakeNative> | null,
  resolveAsset: (asset: number) => { uri: string } | null = () => null
): Facade {
  let facade!: Facade;
  jest.isolateModules(() => {
    jest.doMock('expo', () => ({
      ...jest.requireActual('expo'),
      requireOptionalNativeModule: jest.fn(() => native),
    }));
    // Image.resolveAssetSource delegates to this module; expo registers transformers on it
    jest.doMock('react-native/Libraries/Image/resolveAssetSource', () => ({
      __esModule: true,
      default: Object.assign(resolveAsset, {
        setCustomSourceTransformer: () => {},
        addCustomSourceTransformer: () => {},
        pickScale: () => 1,
      }),
    }));
    require('react-native').Platform.OS = os;
    facade = require('../index').FloatingBubble;
  });
  return facade;
}

const originalOS = Platform.OS;

afterEach(() => {
  jest.dontMock('expo');
  jest.dontMock('react-native/Libraries/Image/resolveAssetSource');
  Platform.OS = originalOS;
});

describe('FloatingBubble without a native module', () => {
  it.each(['ios', 'web'] as const)('is a safe no-op on %s', (os) => {
    const native = fakeNative();
    const b = load(os, native);

    expect(b.isAvailable).toBe(false);
    expect(b.hasOverlayPermission()).toBe(false);
    expect(b.enable()).toBe(false);
    expect(b.bringAppToForeground()).toBe(false);
    expect(b.isEnabled()).toBe(false);
    expect(b.isVisible()).toBe(false);
    expect(() => {
      b.disable();
      b.openOverlayPermissionSettings();
      b.startKeepAlive();
      b.stopKeepAlive();
      b.scheduleBringAppToForeground(4);
      b.cancelScheduledBringAppToForeground();
    }).not.toThrow();
    expect(b.addPressListener(jest.fn()).remove).toEqual(expect.any(Function));
    expect(native.enable).not.toHaveBeenCalled();
  });

  it('is a safe no-op on Android without a development build (Expo Go)', () => {
    const b = load('android', null);

    expect(b.isAvailable).toBe(false);
    expect(b.enable({ size: 60 })).toBe(false);
    expect(() => b.addDismissListener(jest.fn()).remove()).not.toThrow();
  });
});

describe('FloatingBubble on Android', () => {
  it('delegates every call to the native module', () => {
    const native = fakeNative();
    const b = load('android', native);
    const options = {
      size: 72,
      notificationTitle: 'Bubble active',
      showWhen: 'always' as const,
      hideOnPress: false,
    };

    expect(b.isAvailable).toBe(true);
    expect(b.hasOverlayPermission()).toBe(true);
    expect(b.enable(options)).toBe(true);
    expect(native.enable).toHaveBeenCalledWith(options);
    expect(b.bringAppToForeground()).toBe(true);
    expect(b.isEnabled()).toBe(true);
    expect(b.isVisible()).toBe(true);

    b.disable();
    b.openOverlayPermissionSettings();
    b.startKeepAlive(options);
    b.stopKeepAlive();
    b.scheduleBringAppToForeground(4);
    b.cancelScheduledBringAppToForeground();

    expect(native.disable).toHaveBeenCalled();
    expect(native.openOverlayPermissionSettings).toHaveBeenCalled();
    expect(native.startKeepAlive).toHaveBeenCalledWith(options);
    expect(native.stopKeepAlive).toHaveBeenCalled();
    expect(native.scheduleBringAppToForeground).toHaveBeenCalledWith(4);
    expect(native.cancelScheduledBringAppToForeground).toHaveBeenCalled();
  });

  it('enable without options sends an empty object (native defaults, showWhen background)', () => {
    const native = fakeNative();
    load('android', native).enable();

    expect(native.enable).toHaveBeenCalledWith({});
  });

  it('subscribes to the onPress and onDismiss events', () => {
    const native = fakeNative();
    const b = load('android', native);
    const onPress = jest.fn();
    const onDismiss = jest.fn();

    b.addPressListener(onPress);
    b.addDismissListener(onDismiss);

    expect(native.addListener).toHaveBeenCalledWith('onPress', onPress);
    expect(native.addListener).toHaveBeenCalledWith('onDismiss', onDismiss);
  });
});

describe('FloatingBubble icon', () => {
  const DEV_URI = 'http://10.0.2.2:8081/assets/bubble.png?platform=android';

  it('without icon sends no icon, so the native side uses the app icon', () => {
    const native = fakeNative();
    load('android', native).enable({ size: 60 });

    expect(native.enable).toHaveBeenCalledWith({ size: 60 });
  });

  it('turns a require() image into the URI the native side loads', () => {
    const native = fakeNative();
    const b = load('android', native, (asset) => (asset === 42 ? { uri: DEV_URI } : null));

    b.enable({ icon: 42, showWhen: 'always' });
    b.startKeepAlive({ icon: 42 });

    expect(native.enable).toHaveBeenCalledWith({ icon: DEV_URI, showWhen: 'always' });
    expect(native.startKeepAlive).toHaveBeenCalledWith({ icon: DEV_URI });
  });

  it('passes URLs, files, base64 and resource names through unchanged', () => {
    const native = fakeNative();
    const b = load('android', native);

    for (const icon of [
      'https://x.com/a.png',
      'file:///a.png',
      'data:image/png;base64,AA',
      'ic_bubble',
    ]) {
      b.enable({ icon });
      expect(native.enable).toHaveBeenLastCalledWith({ icon });
    }
  });

  it('drops a require() image that cannot be resolved, keeping the app icon', () => {
    const native = fakeNative();
    load('android', native, () => null).enable({ icon: 7, size: 50 });

    expect(native.enable).toHaveBeenCalledWith({ size: 50 });
  });
});
