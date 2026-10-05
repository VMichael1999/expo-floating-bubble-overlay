import { useEffect, useState } from 'react';
import { type BubbleOptions, FloatingBubble, type ShowWhen } from 'expo-floating-bubble-overlay';
import { AppState, Button, SafeAreaView, ScrollView, Switch, Text, View } from 'react-native';

const MODES: ShowWhen[] = ['background', 'foreground', 'always'];
// Any image works: require(), an https URL, a file:// URI, base64 or a native resource name
const CUSTOM_ICON = require('./assets/splash-icon.png');

export default function App() {
  const [hasPermission, setHasPermission] = useState(FloatingBubble.hasOverlayPermission());
  const [enabled, setEnabled] = useState(FloatingBubble.isEnabled());
  const [showWhen, setShowWhen] = useState<ShowWhen>('background');
  const [customIcon, setCustomIcon] = useState(false);
  const [hideOnPress, setHideOnPress] = useState(true);
  const [lastEvent, setLastEvent] = useState('—');

  useEffect(() => {
    const press = FloatingBubble.addPressListener(() => setLastEvent('onPress'));
    // Dropping the bubble on the X disables it: keep the switch in sync
    const dismiss = FloatingBubble.addDismissListener(() => {
      setLastEvent('onDismiss');
      setEnabled(false);
    });
    // Re-read the permission when coming back from Settings
    const appState = AppState.addEventListener('change', (s) => {
      if (s === 'active') setHasPermission(FloatingBubble.hasOverlayPermission());
    });
    return () => {
      press.remove();
      dismiss.remove();
      appState.remove();
    };
  }, []);

  // Without `icon` the bubble shows the app icon
  const options = (mode: ShowWhen, custom: boolean, hide = hideOnPress): BubbleOptions => ({
    showWhen: mode,
    hideOnPress: hide,
    notificationTitle: 'Bubble active',
    ...(custom ? { icon: CUSTOM_ICON } : {}),
  });

  // Like a "Show floating bubble" switch in the app settings
  const toggle = (on: boolean) => {
    if (!on) {
      FloatingBubble.disable();
      setEnabled(false);
      return;
    }
    if (!FloatingBubble.enable(options(showWhen, customIcon))) {
      FloatingBubble.openOverlayPermissionSettings();
      return;
    }
    setEnabled(true);
  };

  // Enabling again applies the new options
  const changeMode = (mode: ShowWhen) => {
    setShowWhen(mode);
    if (enabled) FloatingBubble.enable(options(mode, customIcon));
  };

  const changeIcon = (custom: boolean) => {
    setCustomIcon(custom);
    if (enabled) FloatingBubble.enable(options(showWhen, custom));
  };

  const changeHideOnPress = (hide: boolean) => {
    setHideOnPress(hide);
    if (enabled) FloatingBubble.enable(options(showWhen, customIcon, hide));
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Floating bubble</Text>
        <Group name="Settings">
          <View style={styles.row}>
            <Text>Show floating bubble</Text>
            <Switch value={enabled} onValueChange={toggle} />
          </View>
          <View style={styles.row}>
            <Text>Custom icon (off = app icon)</Text>
            <Switch value={customIcon} onValueChange={changeIcon} />
          </View>
          <View style={styles.row}>
            <Text>Hide on tap</Text>
            <Switch value={hideOnPress} onValueChange={changeHideOnPress} />
          </View>
          <Text>Show when:</Text>
          {MODES.map((mode) => (
            <Button
              key={mode}
              title={mode === showWhen ? `● ${mode}` : mode}
              onPress={() => changeMode(mode)}
            />
          ))}
        </Group>
        <Group name="State">
          <Text>Available: {String(FloatingBubble.isAvailable)}</Text>
          <Text>Permission: {String(hasPermission)}</Text>
          <Text>Last event: {lastEvent}</Text>
        </Group>
      </ScrollView>
    </SafeAreaView>
  );
}

function Group(props: { name: string; children: React.ReactNode }) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupHeader}>{props.name}</Text>
      {props.children}
    </View>
  );
}

const styles = {
  header: { fontSize: 30, margin: 20 },
  groupHeader: { fontSize: 20, marginBottom: 20 },
  group: { margin: 20, backgroundColor: '#fff', borderRadius: 10, padding: 20, gap: 8 },
  container: { flex: 1, backgroundColor: '#eee' },
  row: { flexDirection: 'row' as const, justifyContent: 'space-between' as const, alignItems: 'center' as const },
};
