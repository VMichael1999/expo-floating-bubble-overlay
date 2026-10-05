import { Platform } from 'react-native';

type Facade = typeof import('../index').FloatingBubble;

function fakeNative() {
  return {
    hasOverlayPermission: jest.fn(() => true),
    openOverlayPermissionSettings: jest.fn(),
    show: jest.fn(() => true),
    startKeepAlive: jest.fn(),
    stopKeepAlive: jest.fn(),
    hide: jest.fn(),
    bringAppToForeground: jest.fn(() => true),
    scheduleBringAppToForeground: jest.fn(),
    cancelScheduledBringAppToForeground: jest.fn(),
    isVisible: jest.fn(() => true),
    addListener: jest.fn(() => ({ remove: jest.fn() })),
  };
}

/** Loads the facade with the given platform and native module. */
function load(os: typeof Platform.OS, native: ReturnType<typeof fakeNative> | null): Facade {
  let facade!: Facade;
  jest.isolateModules(() => {
    jest.doMock('expo', () => ({
      ...jest.requireActual('expo'),
      requireOptionalNativeModule: jest.fn(() => native),
    }));
    require('react-native').Platform.OS = os;
    facade = require('../index').FloatingBubble;
  });
  return facade;
}

const originalOS = Platform.OS;

afterEach(() => {
  jest.dontMock('expo');
  Platform.OS = originalOS;
});

describe('FloatingBubble without a native module', () => {
  it.each(['ios', 'web'] as const)('is a safe no-op on %s', (os) => {
    const native = fakeNative();
    const b = load(os, native);

    expect(b.isAvailable).toBe(false);
    expect(b.hasOverlayPermission()).toBe(false);
    expect(b.show()).toBe(false);
    expect(b.bringAppToForeground()).toBe(false);
    expect(b.isVisible()).toBe(false);
    expect(() => {
      b.hide();
      b.openOverlayPermissionSettings();
      b.startKeepAlive();
      b.stopKeepAlive();
      b.scheduleBringAppToForeground(4);
      b.cancelScheduledBringAppToForeground();
    }).not.toThrow();
    expect(b.addPressListener(jest.fn()).remove).toEqual(expect.any(Function));
    expect(native.show).not.toHaveBeenCalled();
  });

  it('is a safe no-op on Android without a development build (Expo Go)', () => {
    const b = load('android', null);

    expect(b.isAvailable).toBe(false);
    expect(b.show({ size: 60 })).toBe(false);
    expect(() => b.addDismissListener(jest.fn()).remove()).not.toThrow();
  });
});

describe('FloatingBubble on Android', () => {
  it('delegates every call to the native module', () => {
    const native = fakeNative();
    const b = load('android', native);
    const options = { size: 72, notificationTitle: 'Bubble active' };

    expect(b.isAvailable).toBe(true);
    expect(b.hasOverlayPermission()).toBe(true);
    expect(b.show(options)).toBe(true);
    expect(native.show).toHaveBeenCalledWith(options);
    expect(b.bringAppToForeground()).toBe(true);
    expect(b.isVisible()).toBe(true);

    b.hide();
    b.openOverlayPermissionSettings();
    b.startKeepAlive(options);
    b.stopKeepAlive();
    b.scheduleBringAppToForeground(4);
    b.cancelScheduledBringAppToForeground();

    expect(native.hide).toHaveBeenCalled();
    expect(native.openOverlayPermissionSettings).toHaveBeenCalled();
    expect(native.startKeepAlive).toHaveBeenCalledWith(options);
    expect(native.stopKeepAlive).toHaveBeenCalled();
    expect(native.scheduleBringAppToForeground).toHaveBeenCalledWith(4);
    expect(native.cancelScheduledBringAppToForeground).toHaveBeenCalled();
  });

  it('show without options sends an empty object', () => {
    const native = fakeNative();
    load('android', native).show();

    expect(native.show).toHaveBeenCalledWith({});
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
