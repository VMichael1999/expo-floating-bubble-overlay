import { useEffect, useState } from 'react';
import { FloatingBubble } from 'expo-floating-bubble-overlay';
import { AppState, Button, SafeAreaView, ScrollView, Text, View } from 'react-native';

export default function App() {
  const [hasPermission, setHasPermission] = useState(FloatingBubble.hasOverlayPermission());
  const [lastEvent, setLastEvent] = useState('—');

  useEffect(() => {
    const press = FloatingBubble.addPressListener(() => setLastEvent('onPress'));
    const dismiss = FloatingBubble.addDismissListener(() => setLastEvent('onDismiss'));
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

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Floating bubble</Text>
        <Group name="State">
          <Text>Available: {String(FloatingBubble.isAvailable)}</Text>
          <Text>Permission: {String(hasPermission)}</Text>
          <Text>Last event: {lastEvent}</Text>
        </Group>
        <Group name="Actions">
          <Button
            title="Open permission settings"
            onPress={FloatingBubble.openOverlayPermissionSettings}
          />
          <Button
            title="Show bubble (leave the app to see it)"
            onPress={() => FloatingBubble.show({ notificationTitle: 'Bubble active' })}
          />
          <Button title="Hide bubble" onPress={FloatingBubble.hide} />
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
};
