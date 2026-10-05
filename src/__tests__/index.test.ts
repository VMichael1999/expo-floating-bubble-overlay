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
    const options = { size: 72, notificationTitle: 'Bubble active', showWhen: 'always' as const };

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
