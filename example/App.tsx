import { type ReactNode, useCallback, useEffect, useState } from 'react';
import { type BubbleOptions, FloatingBubble, type ShowWhen } from 'expo-floating-bubble-overlay';
import Constants from 'expo-constants';
import {
  AppState,
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';

const MODES: { value: ShowWhen; label: string }[] = [
  { value: 'background', label: 'Background' },
  { value: 'foreground', label: 'Foreground' },
  { value: 'always', label: 'Always' },
];

// Any image works: require(), an https URL, a file:// URI, base64 or a native resource name
const CUSTOM_ICON = require('./assets/splash-icon.png');

// Android 13+ asks the user before showing notifications (the bubble's service notification)
const NEEDS_NOTIFICATION_PERMISSION = Platform.OS === 'android' && Number(Platform.Version) >= 33;

async function hasNotificationPermission() {
  if (!NEEDS_NOTIFICATION_PERMISSION) return true;
  return PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
}

export default function App() {
  const [overlayGranted, setOverlayGranted] = useState(FloatingBubble.hasOverlayPermission());
  const [notificationsGranted, setNotificationsGranted] = useState(!NEEDS_NOTIFICATION_PERMISSION);
  const [enabled, setEnabled] = useState(FloatingBubble.isEnabled());
  const [showWhen, setShowWhen] = useState<ShowWhen>('background');
  const [customIcon, setCustomIcon] = useState(false);
  const [hideOnPress, setHideOnPress] = useState(true);
  const [lastEvent, setLastEvent] = useState('—');

  const refreshPermissions = useCallback(async () => {
    setOverlayGranted(FloatingBubble.hasOverlayPermission());
    setNotificationsGranted(await hasNotificationPermission());
  }, []);

  useEffect(() => {
    void refreshPermissions();
    const press = FloatingBubble.addPressListener(() => setLastEvent('onPress'));
    // Dropping the bubble on the X disables it: keep the switch in sync
    const dismiss = FloatingBubble.addDismissListener(() => {
      setLastEvent('onDismiss');
      setEnabled(false);
    });
    // Coming back from the system settings: read the permissions again
    const appState = AppState.addEventListener('change', (s) => {
      if (s === 'active') void refreshPermissions();
    });
    return () => {
      press.remove();
      dismiss.remove();
      appState.remove();
    };
  }, [refreshPermissions]);

  const requestNotifications = async () => {
    await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
    await refreshPermissions();
  };

  // Without `icon` the bubble shows the app icon
  const options = (
    mode = showWhen,
    custom = customIcon,
    hide = hideOnPress
  ): BubbleOptions => ({
    showWhen: mode,
    hideOnPress: hide,
    notificationTitle: 'Floating bubble example',
    notificationText: 'Tap to return to the app',
    ...(custom ? { icon: CUSTOM_ICON } : {}),
  });

  // Like a "Show floating bubble" switch in the app settings
  const toggle = (on: boolean) => {
    if (!on) {
      FloatingBubble.disable();
      setEnabled(false);
      return;
    }
    setEnabled(FloatingBubble.enable(options()));
  };

  // Enabling again applies the new options
  const apply = (next: BubbleOptions) => {
    if (enabled) FloatingBubble.enable(next);
  };

  return (
    // Android draws the app edge to edge: leave room for the status bar
    <View style={[styles.screen, { paddingTop: Constants.statusBarHeight }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>Floating bubble</Text>
        <Text style={styles.subtitle}>expo-floating-bubble-overlay example</Text>

        {!FloatingBubble.isAvailable && (
          <Card>
            <Text style={styles.warning}>
              The floating bubble is only available on Android in a development or production
              build (not in Expo Go, iOS or web).
            </Text>
          </Card>
        )}

        <Card title="1. Permissions">
          <PermissionRow
            name="Display over other apps"
            description="Required to draw the bubble over other apps."
            granted={overlayGranted}
            action="Grant"
            onPress={FloatingBubble.openOverlayPermissionSettings}
          />
          {NEEDS_NOTIFICATION_PERMISSION && (
            <PermissionRow
              name="Notifications"
              description="Shows the notification of the service that keeps the app alive."
              granted={notificationsGranted}
              action="Allow"
              onPress={requestNotifications}
            />
          )}
        </Card>

        <Card title="2. Bubble">
          <SwitchRow
            label="Show floating bubble"
            hint={overlayGranted ? undefined : 'Grant "Display over other apps" first.'}
            value={enabled}
            disabled={!overlayGranted}
            onValueChange={toggle}
          />
          <SwitchRow
            label="Custom icon"
            hint="Off: your app icon (default)."
            value={customIcon}
            onValueChange={(custom) => {
              setCustomIcon(custom);
              apply(options(showWhen, custom));
            }}
          />
          <SwitchRow
            label="Hide on tap"
            hint="On: a tap hides it until the app goes to the background again."
            value={hideOnPress}
            onValueChange={(hide) => {
              setHideOnPress(hide);
              apply(options(showWhen, customIcon, hide));
            }}
          />
          <Text style={styles.label}>Show when</Text>
          <View style={styles.segmented}>
            {MODES.map(({ value, label }) => (
              <Pressable
                key={value}
                accessibilityRole="button"
                accessibilityState={{ selected: value === showWhen }}
                style={[styles.segment, value === showWhen && styles.segmentSelected]}
                onPress={() => {
                  setShowWhen(value);
                  apply(options(value));
                }}>
                <Text style={[styles.segmentText, value === showWhen && styles.segmentTextSelected]}>
                  {label}
                </Text>
              </Pressable>
            ))}
          </View>
        </Card>

        <Card title="3. State">
          <StateRow label="Available" value={String(FloatingBubble.isAvailable)} />
          <StateRow label="Enabled" value={String(enabled)} />
          <StateRow label="Last event" value={lastEvent} />
        </Card>
      </ScrollView>
    </View>
  );
}

function Card(props: { title?: string; children: ReactNode }) {
  return (
    <View style={styles.card}>
      {props.title && <Text style={styles.cardTitle}>{props.title}</Text>}
      {props.children}
    </View>
  );
}

function PermissionRow(props: {
  name: string;
  description: string;
  granted: boolean;
  action: string;
  onPress: () => void;
}) {
  return (
    <View style={styles.permission}>
      <View style={styles.flex}>
        <Text style={styles.label}>{props.name}</Text>
        <Text style={styles.hint}>{props.description}</Text>
      </View>
      {props.granted ? (
        <Text style={[styles.badge, styles.badgeGranted]}>Granted</Text>
      ) : (
        <Pressable accessibilityRole="button" style={styles.button} onPress={props.onPress}>
          <Text style={styles.buttonText}>{props.action}</Text>
        </Pressable>
      )}
    </View>
  );
}

function SwitchRow(props: {
  label: string;
  hint?: string;
  value: boolean;
  disabled?: boolean;
  onValueChange: (value: boolean) => void;
}) {
  return (
    <View style={styles.row}>
      <View style={styles.flex}>
        <Text style={[styles.label, props.disabled && styles.disabled]}>{props.label}</Text>
        {props.hint && <Text style={styles.hint}>{props.hint}</Text>}
      </View>
      <Switch value={props.value} disabled={props.disabled} onValueChange={props.onValueChange} />
    </View>
  );
}

function StateRow(props: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.hint}>{props.label}</Text>
      <Text style={styles.label}>{props.value}</Text>
    </View>
  );
}

const PRIMARY = '#1A73E8';

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F2F4F7' },
  content: { padding: 16, gap: 16 },
  title: { fontSize: 28, fontWeight: '700', color: '#101828', marginTop: 8 },
  subtitle: { fontSize: 14, color: '#667085', marginTop: -12 },
  card: { backgroundColor: '#fff', borderRadius: 16, padding: 16, gap: 14 },
  cardTitle: { fontSize: 13, fontWeight: '700', color: '#667085', textTransform: 'uppercase' },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  permission: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  flex: { flex: 1 },
  label: { fontSize: 16, color: '#101828' },
  hint: { fontSize: 13, color: '#667085', marginTop: 2 },
  disabled: { color: '#98A2B3' },
  warning: { fontSize: 14, color: '#B54708' },
  badge: { fontSize: 13, fontWeight: '600', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999 },
  badgeGranted: { color: '#067647', backgroundColor: '#DCFAE6' },
  button: { backgroundColor: PRIMARY, borderRadius: 999, paddingHorizontal: 16, paddingVertical: 8 },
  buttonText: { color: '#fff', fontWeight: '600' },
  segmented: { flexDirection: 'row', backgroundColor: '#F2F4F7', borderRadius: 12, padding: 4 },
  segment: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: 9 },
  segmentSelected: { backgroundColor: '#fff', elevation: 1 },
  segmentText: { fontSize: 14, color: '#667085', fontWeight: '600' },
  segmentTextSelected: { color: PRIMARY },
});
