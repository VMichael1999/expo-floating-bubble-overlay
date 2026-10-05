import { useEffect, useState } from 'react';
import { BurbujaFlotante } from 'expo-floating-bubble-overlay';
import { AppState, Button, SafeAreaView, ScrollView, Text, View } from 'react-native';

export default function App() {
  const [permiso, setPermiso] = useState(BurbujaFlotante.tienePermiso());
  const [ultimoEvento, setUltimoEvento] = useState('—');

  useEffect(() => {
    const tocar = BurbujaFlotante.alTocar(() => setUltimoEvento('onTocar'));
    const cerrar = BurbujaFlotante.alCerrar(() => setUltimoEvento('onCerrar'));
    // Al volver de Ajustes se relee el permiso
    const estado = AppState.addEventListener('change', (s) => {
      if (s === 'active') setPermiso(BurbujaFlotante.tienePermiso());
    });
    return () => {
      tocar.remove();
      cerrar.remove();
      estado.remove();
    };
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Floating bubble</Text>
        <Group name="Estado">
          <Text>Disponible: {String(BurbujaFlotante.disponible)}</Text>
          <Text>Permiso: {String(permiso)}</Text>
          <Text>Último evento: {ultimoEvento}</Text>
        </Group>
        <Group name="Acciones">
          <Button title="Abrir ajustes del permiso" onPress={BurbujaFlotante.abrirAjustesPermiso} />
          <Button
            title="Mostrar burbuja (sal de la app para verla)"
            onPress={() => BurbujaFlotante.mostrar({ tituloNotificacion: 'Burbuja activa' })}
          />
          <Button title="Ocultar burbuja" onPress={BurbujaFlotante.ocultar} />
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
