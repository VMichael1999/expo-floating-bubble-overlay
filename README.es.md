# expo-floating-bubble-overlay

🌐 [English](README.md) | **Español**

Una burbuja flotante tipo "chat head" para **Android** que se dibuja sobre otras apps y vuelve a traer tu app de Expo / React Native al frente con un toque.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Expo Modules API](https://img.shields.io/badge/Expo%20Modules-Kotlin-000020?logo=expo)

**Tú decides todo:** si la burbuja está activada o no, cuándo se muestra (segundo plano, primer plano o ambos), qué imagen muestra, su tamaño y su comportamiento. No está pensada para un tipo de app en concreto.

## Capturas de pantalla

Tomadas con la [app de ejemplo](#app-de-ejemplo) en un emulador Pixel 10 Pro XL (Android 17). De izquierda a derecha:

<p align="center">
  <img src="docs/screenshots/01-settings.webp" width="150" alt="Ajustes de la app de ejemplo con los permisos concedidos"/>
  <img src="docs/screenshots/02-background.webp" width="150" alt="Burbuja con el icono de la app sobre el escritorio"/>
  <img src="docs/screenshots/03-drag-to-dismiss.webp" width="150" alt="Burbuja arrastrada hacia la X de abajo"/>
  <img src="docs/screenshots/04-custom-icon.webp" width="150" alt="Burbuja con una imagen propia sobre el escritorio"/>
  <img src="docs/screenshots/05-always.webp" width="150" alt="Burbuja sobre la propia app"/>
</p>

1. **Ajustes**: permisos y opciones.
2. **En segundo plano**: el icono de tu app por defecto, pegada al borde.
3. **Arrastrar para cerrar**: la ✕ aparece al arrastrar.
4. **Icono personalizado**: cualquier imagen con `icon`.
5. **`showWhen: 'always'`**: también dentro de tu app.

## Contenido

- [Capturas de pantalla](#capturas-de-pantalla)
- [Características](#características)
- [Casos de uso](#casos-de-uso)
- [Requisitos](#requisitos)
- [Instalación](#instalación)
- [Inicio rápido](#inicio-rápido)
- [Guía](#guía)
  - [1. Permiso](#1-permiso-mostrar-sobre-otras-apps)
  - [2. Activar y desactivar la burbuja](#2-activar-y-desactivar-la-burbuja)
  - [3. Cuándo se muestra: `showWhen`](#3-cuándo-se-muestra-showwhen)
  - [4. La imagen de la burbuja: `icon`](#4-la-imagen-de-la-burbuja-icon)
  - [5. Apariencia y comportamiento](#5-apariencia-y-comportamiento)
  - [6. Eventos](#6-eventos)
  - [7. La notificación y el servicio en segundo plano](#7-la-notificación-y-el-servicio-en-segundo-plano)
  - [8. Traer la app al frente](#8-traer-la-app-al-frente)
  - [9. Config plugin](#9-config-plugin)
- [Referencia de la API](#referencia-de-la-api)
- [Plataformas](#plataformas)
- [Google Play](#google-play)
- [Solución de problemas](#solución-de-problemas)
- [App de ejemplo](#app-de-ejemplo)
- [Desarrollo](#desarrollo)
- [Licencia](#licencia)

## Características

- **Burbuja flotante sobre cualquier app**: tócala para volver a la tuya, arrástrala a donde quieras, suéltala sobre la ✕ para cerrarla.
- **Tú eliges qué hace un toque**: por defecto se abre la app y la burbuja se oculta hasta la próxima vez que la app pase a segundo plano; o mantenla en pantalla con `hideOnPress: false`.
- **Activar y desactivar a voluntad**: `enable()` y `disable()`, ideal para un interruptor "Mostrar burbuja flotante" en los ajustes de tu app.
- **Tú eliges cuándo se muestra**: solo en segundo plano (por defecto), solo en primer plano o siempre.
- **Automática**: una vez activada, el paquete muestra y oculta la burbuja solo cuando la app pasa a segundo plano y vuelve. No hace falta código con `AppState`, y funciona aunque JavaScript esté pausado.
- **El icono de tu app por defecto, cualquier imagen si lo prefieres**: `require()`, una URL https, un archivo, base64 o un recurso nativo.
- **Personalizable**: tamaño, opacidad, posición inicial, pegarse al borde, distancia para cerrar y textos de la notificación.
- **Mantiene tu app viva** en segundo plano con un servicio en primer plano mientras la burbuja se ve, o cuando lo pidas.
- **Trae la app al frente** desde segundo plano, al instante o tras unos segundos (temporizador nativo).
- **Segura en todas partes**: en iOS, web y Expo Go cada llamada no hace nada, así que el mismo código funciona en todas las plataformas.
- **Config plugin** para declarar por qué tu app usa el servicio en primer plano (Google Play).
- Escrita en Kotlin con la Expo Modules API y totalmente tipada en TypeScript.

## Casos de uso

La burbuja es una herramienta genérica. Algunas ideas:

- **Mensajería y chat de soporte**: un acceso directo a una conversación en curso.
- **Transporte, delivery y logística**: los conductores saltan a la app de navegación y vuelven con un toque.
- **Música, podcasts y temporizadores**: acceso rápido mientras el usuario está en otra app.
- **Llamadas y streaming**: volver a una llamada o transmisión activa.
- **Productividad**: notas, traductores o cualquier herramienta que deba estar a un toque.

## Requisitos

| | |
|---|---|
| Plataforma | Android 7.0+ (API 24) |
| Expo | Un [development build](https://docs.expo.dev/develop/development-builds/introduction/) (Expo Go no incluye código nativo propio). Construido con Expo SDK 57. |
| React Native | Funciona en proyectos React Native "bare" con [Expo Modules](https://docs.expo.dev/bare/installing-expo-modules/) instalado |
| Permiso | "Mostrar sobre otras apps" (`SYSTEM_ALERT_WINDOW`), que el usuario concede en los ajustes del sistema |

## Instalación

```bash
npx expo install expo-floating-bubble-overlay
```

Después, vuelve a compilar la app nativa:

```bash
npx expo run:android
```

El paquete añade a tu manifest de Android el permiso `SYSTEM_ALERT_WINDOW`, los permisos del servicio en primer plano y su servicio. No necesitas editar ningún archivo nativo.

Opcionalmente, añade el [config plugin](#9-config-plugin) a `app.json` para describir tu servicio en primer plano ante Google Play.

## Inicio rápido

```tsx
import { FloatingBubble } from 'expo-floating-bubble-overlay';

// 1. Pide el permiso una vez (abre la pantalla del sistema)
if (!FloatingBubble.hasOverlayPermission()) {
  FloatingBubble.openOverlayPermissionSettings();
}

// 2. Activa la burbuja: desde ahora aparece cuando la app pasa a segundo plano
FloatingBubble.enable();

// 3. Desactívala cuando quieras
FloatingBubble.disable();
```

Eso es todo. Con los valores por defecto, la burbuja muestra **el icono de tu app**, aparece **solo mientras la app está en segundo plano**, y al tocarla **trae la app de vuelta y se oculta** hasta la próxima vez que la app pase a segundo plano.

## Guía

### 1. Permiso: "Mostrar sobre otras apps"

Android exige que el usuario permita a tu app dibujar sobre otras apps. No hay un diálogo en tiempo de ejecución: el usuario lo activa en una pantalla del sistema.

```tsx
import { useEffect, useState } from 'react';
import { AppState } from 'react-native';
import { FloatingBubble } from 'expo-floating-bubble-overlay';

function useOverlayPermission() {
  const [granted, setGranted] = useState(FloatingBubble.hasOverlayPermission());

  useEffect(() => {
    // El usuario vuelve de la pantalla de ajustes: leer el permiso de nuevo
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') setGranted(FloatingBubble.hasOverlayPermission());
    });
    return () => sub.remove();
  }, []);

  return { granted, request: FloatingBubble.openOverlayPermissionSettings };
}
```

- `hasOverlayPermission()` es síncrono.
- `openOverlayPermissionSettings()` abre la página de tu app en "Mostrar sobre otras apps". En dispositivos sin esa página, abre los detalles de tu app.
- `enable()` devuelve `false` si falta el permiso, para que puedas reaccionar.

> **Consejo:** explica al usuario por qué necesitas la burbuja antes de mandarlo a la pantalla de ajustes.

### 2. Activar y desactivar la burbuja

```ts
FloatingBubble.enable(options?);   // activar (devuelve false sin permiso)
FloatingBubble.disable();          // desactivar y quitarla de la pantalla
FloatingBubble.isEnabled();        // ¿está activada?
FloatingBubble.isVisible();        // ¿se ve en pantalla ahora mismo?
```

- **Activada** significa "la burbuja está encendida". **Visible** significa "está en pantalla ahora mismo". Una burbuja activada puede estar oculta, por ejemplo mientras tu app está en primer plano con el `showWhen` por defecto.
- Llamar de nuevo a `enable()` con otras opciones **las aplica** (la burbuja se recrea).
- Si el usuario suelta la burbuja sobre la ✕, queda **desactivada** y recibes [`onDismiss`](#6-eventos). Tu app decide si la vuelve a activar.
- Si el usuario cierra tu app desde las apps recientes, la burbuja se va con ella.

**Ejemplo: un interruptor "Mostrar burbuja flotante" en tu pantalla de ajustes.** Guardar la preferencia le corresponde a tu app (AsyncStorage, MMKV, SecureStore, tu backend…):

```tsx
import { useEffect, useState } from 'react';
import { Switch } from 'react-native';
import { FloatingBubble } from 'expo-floating-bubble-overlay';

function FloatingBubbleSetting({ saved, save }: { saved: boolean; save: (on: boolean) => void }) {
  const [enabled, setEnabled] = useState(saved);

  useEffect(() => {
    // Restaurar la elección del usuario al iniciar la app
    if (saved) setEnabled(FloatingBubble.enable());
    // Mantener el interruptor sincronizado si el usuario suelta la burbuja en la X
    const sub = FloatingBubble.addDismissListener(() => {
      setEnabled(false);
      save(false);
    });
    return () => sub.remove();
  }, []);

  const toggle = (on: boolean) => {
    if (!on) {
      FloatingBubble.disable();
    } else if (!FloatingBubble.enable()) {
      FloatingBubble.openOverlayPermissionSettings();
      return;
    }
    setEnabled(on);
    save(on);
  };

  return <Switch value={enabled} onValueChange={toggle} />;
}
```

### 3. Cuándo se muestra: `showWhen`

| `showWhen` | La burbuja se muestra… |
|---|---|
| `'background'` **(por defecto)** | solo mientras tu app está en segundo plano (el usuario está en otra app o en el escritorio) |
| `'foreground'` | solo mientras tu app está en primer plano |
| `'always'` | en ambos estados |

```ts
FloatingBubble.enable();                           // solo en segundo plano (por defecto)
FloatingBubble.enable({ showWhen: 'foreground' }); // solo dentro de tu app
FloatingBubble.enable({ showWhen: 'always' });     // en todas partes
```

El paquete sigue a tu app entre primer y segundo plano de forma nativa (ciclo de vida de las actividades de Android), así que funciona aunque JavaScript esté pausado. Un diálogo del sistema sobre tu app (por ejemplo, una petición de permiso) **no** cuenta como salir de la app, así que la burbuja no aparece por eso.

### 4. La imagen de la burbuja: `icon`

**Por defecto, la burbuja muestra el icono de tu app.** No tienes que hacer nada para eso.

Si quieres otra imagen, pasa `icon`:

| Origen | Ejemplo |
|---|---|
| Imagen local de tu proyecto | `icon: require('./assets/bubble.png')` |
| Imagen remota (https) | `icon: 'https://example.com/avatar.png'` |
| Archivo del dispositivo | `icon: 'file:///data/user/0/your.app/files/photo.png'` |
| Data URI en base64 | `icon: 'data:image/png;base64,iVBORw0KGgo…'` |
| Recurso nativo de Android (drawable/mipmap) | `icon: 'ic_bubble'` |

```ts
FloatingBubble.enable({ icon: require('./assets/bubble.png') });
```

Cómo se comporta:

- La burbuja aparece **al instante** con el icono de tu app. Una imagen remota, un archivo o un base64 se cargan en segundo plano y lo reemplazan en cuanto llegan.
- Si la imagen **no se puede cargar** (sin conexión, 404, archivo o recurso inexistente), la burbuja **mantiene el icono de tu app**. Nunca falla.
- Las imágenes grandes se reducen al tamaño de la burbuja. Límites: 5 MB por imagen y 10 s para descargarla.
- La imagen se dibuja dentro de un círculo blanco y se recorta a él. Las imágenes cuadradas con algo de margen se ven mejor.
- Con `require()`, Metro sirve la imagen en desarrollo y queda incluida en la app en los builds de release.

### 5. Apariencia y comportamiento

Todas las opciones son opcionales. Tamaños y posiciones en dp.

| Opción | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `showWhen` | `'background' \| 'foreground' \| 'always'` | `'background'` | Cuándo se ve la burbuja activada. Ver [`showWhen`](#3-cuándo-se-muestra-showwhen). |
| `hideOnPress` | `boolean` | `true` | Un toque oculta la burbuja hasta que la app vuelva a segundo plano. `false` la mantiene en pantalla. Ver [Tocar la burbuja](#tocar-la-burbuja). |
| `icon` | `number \| string` | el icono de tu app | Imagen de la burbuja. Ver [`icon`](#4-la-imagen-de-la-burbuja-icon). |
| `size` | `number` | `60` | Diámetro, de 40 a 96. |
| `opacity` | `number` | `1` | De 0.2 a 1. |
| `x`, `y` | `number` | borde derecho, a un tercio de la altura | Posición inicial. |
| `snapToEdge` | `boolean` | `true` | Al soltarla, la burbuja va al borde lateral más cercano. |
| `dismissDistance` | `number` | `96` | Qué tan cerca de la ✕ hay que soltarla para cerrarla. |
| `notificationTitle` | `string` | el nombre de tu app | Título de la [notificación](#7-la-notificación-y-el-servicio-en-segundo-plano). |
| `notificationText` | `string` | `"Tap to return to the app"` | Texto de la notificación. |

Los valores fuera de rango se ajustan al límite (por ejemplo, `size: 200` pasa a 96).

#### Tocar la burbuja

| `hideOnPress` | Qué hace un toque |
|---|---|
| `true` **(por defecto)** | Abre tu app (si estaba en segundo plano) y **la burbuja desaparece**. **Vuelve sola** la próxima vez que tu app pase a segundo plano. |
| `false` | Abre tu app (si estaba en segundo plano) y **la burbuja se queda en pantalla**. Útil con `showWhen: 'always'`. |

```ts
FloatingBubble.enable();                                           // un toque la oculta (por defecto)
FloatingBubble.enable({ showWhen: 'always', hideOnPress: false }); // un toque la mantiene
```

En ambos casos la burbuja sigue **activada** y recibes [`onPress`](#6-eventos). Llamar de nuevo a `enable()` muestra una burbuja que un toque había ocultado.

#### Gestos

- **Tocar**: vuelve a tu app y, por defecto, oculta la burbuja (ver [Tocar la burbuja](#tocar-la-burbuja)).
- **Arrastrar**: aparece una ✕ abajo de la pantalla. Cerca de ella, la burbuja se pega con una ligera vibración.
- **Soltar sobre la ✕**: la burbuja se desactiva (ver [`onDismiss`](#6-eventos)).
- **Soltar en otro sitio**: la burbuja va al borde lateral más cercano (o se queda donde está con `snapToEdge: false`).
- **Accesibilidad**: TalkBack lee "Return to *nombre de tu app*", y un doble toque funciona como un toque.

### 6. Eventos

```ts
const press = FloatingBubble.addPressListener(() => {
  // Se tocó la burbuja
});
const dismiss = FloatingBubble.addDismissListener(() => {
  // El usuario soltó la burbuja en la X: ahora está desactivada
});

// Más tarde
press.remove();
dismiss.remove();
```

| Evento | Cuándo | Lo que el paquete ya hizo |
|---|---|---|
| `onPress` | El usuario toca la burbuja | Si tu app estaba en segundo plano, ya viene al frente. Con `hideOnPress` (por defecto) la burbuja se oculta hasta que la app vuelva a segundo plano. Sigue activada. |
| `onDismiss` | El usuario suelta la burbuja sobre la ✕ | La burbuja queda desactivada hasta que vuelvas a llamar a `enable()`. |

### 7. La notificación y el servicio en segundo plano

Mientras la burbuja está en pantalla, el paquete ejecuta un **servicio en primer plano** de Android. Evita que Android cierre tu app en segundo plano y muestra una notificación fija. Tocar la notificación abre tu app. El servicio se detiene cuando la burbuja desaparece.

- Título: `notificationTitle` (por defecto: el nombre de tu app).
- Texto: `notificationText` (por defecto: "Tap to return to the app").
- Canal de notificación: "Floating bubble" (importancia baja, sin sonido).
- Si vuelves a llamar a `enable()` o `startKeepAlive()` con otros textos, la notificación se actualiza.
- En Android 13+ la notificación solo se ve si tu app tiene el permiso de notificaciones (`POST_NOTIFICATIONS`). El paquete no lo pide; el servicio funciona igual.

**Mantener la app viva sin la burbuja.** Úsalo cuando tu app deba seguir trabajando en segundo plano (por ejemplo, mientras el usuario está "en línea"), con o sin burbuja y sin el permiso de superposición:

```ts
FloatingBubble.startKeepAlive({
  notificationTitle: 'En línea',
  notificationText: 'Esperando nuevos pedidos',
});

FloatingBubble.stopKeepAlive();
```

Llama a `startKeepAlive()` **mientras tu app está en pantalla**: Android no permite iniciar un servicio en primer plano desde segundo plano.

### 8. Traer la app al frente

```ts
FloatingBubble.bringAppToForeground();           // ahora; devuelve false si no pudo
FloatingBubble.scheduleBringAppToForeground(4);  // en 4 segundos (temporizador nativo)
FloatingBubble.cancelScheduledBringAppToForeground();
```

- Necesita el permiso "Mostrar sobre otras apps": Android solo deja que las apps con ese permiso se abran solas desde segundo plano.
- `scheduleBringAppToForeground` usa un temporizador nativo, así que se cumple aunque JavaScript esté pausado en segundo plano. Ejemplo: llega un evento nuevo, muestras una notificación y, si el usuario no reacciona en unos segundos, tu app se abre sola.
- La app se abre en el mismo estado, como al tocar su icono en el escritorio.

### 9. Config plugin

En Android 14+, el tipo de servicio en primer plano `specialUse` exige una breve descripción de por qué tu app lo usa. Google Play la revisa. El paquete trae una descripción genérica:

> Keeps the app running in the background and shows a floating shortcut to return to it from other apps

Para usar la tuya, añade el plugin a `app.json` / `app.config.js` y vuelve a compilar:

```json
{
  "expo": {
    "plugins": [
      [
        "expo-floating-bubble-overlay",
        {
          "specialUseDescription": "Keeps the app running while the driver is online to receive trip requests"
        }
      ]
    ]
  }
}
```

| Opción | Tipo | Descripción |
|---|---|---|
| `specialUseDescription` | `string` | Reemplaza la descripción del paquete en el manifest final de tu app. |

El plugin es opcional: sin él, el paquete funciona con su descripción genérica.

## Referencia de la API

Todo se exporta desde el paquete:

```ts
import {
  FloatingBubble,
  type BubbleOptions,
  type ShowWhen,
} from 'expo-floating-bubble-overlay';
```

| Miembro | Devuelve | Descripción |
|---|---|---|
| `isAvailable` | `boolean` | `false` en iOS, web y Expo Go. |
| `hasOverlayPermission()` | `boolean` | Si "Mostrar sobre otras apps" está concedido. |
| `openOverlayPermissionSettings()` | `void` | Abre la pantalla del sistema para concederlo. |
| `enable(options?)` | `boolean` | Activa la burbuja (o aplica opciones nuevas). `false` si no está disponible o falta el permiso. |
| `disable()` | `void` | Desactiva la burbuja. |
| `isEnabled()` | `boolean` | Si la burbuja está activada. |
| `isVisible()` | `boolean` | Si la burbuja está en pantalla ahora mismo. |
| `addPressListener(listener)` | `{ remove() }` | Se suscribe a `onPress`. |
| `addDismissListener(listener)` | `{ remove() }` | Se suscribe a `onDismiss`. |
| `startKeepAlive(options?)` | `void` | Mantiene la app viva con un servicio en primer plano. Usa `notificationTitle` / `notificationText`. |
| `stopKeepAlive()` | `void` | Lo detiene (el servicio sigue mientras la burbuja lo necesite). |
| `bringAppToForeground()` | `boolean` | Trae la app al frente. `false` si no pudo. |
| `scheduleBringAppToForeground(seconds)` | `void` | Lo mismo, tras unos segundos, con un temporizador nativo. |
| `cancelScheduledBringAppToForeground()` | `void` | Cancela la apertura programada. |

## Plataformas

| Plataforma | Comportamiento |
|---|---|
| Android (development build o build de producción) | Soporte completo. |
| Android en Expo Go | No disponible: `isAvailable` es `false` y cada llamada no hace nada. Usa un development build. |
| iOS | No disponible: iOS no permite que las apps se dibujen sobre otras apps. Cada llamada no hace nada. |
| Web | No disponible. Cada llamada no hace nada. |

Como cada llamada es segura en todas partes, puedes usar el mismo código en todas las plataformas. Usa `FloatingBubble.isAvailable` para ocultar los ajustes de la burbuja donde no aplican.

## Google Play

- **`SYSTEM_ALERT_WINDOW`**: permitido en apps donde dibujar sobre otras apps es una función principal. Prepárate para explicar su uso en la revisión de tu ficha.
- **Servicio en primer plano `specialUse`**: decláralo en Play Console (Contenido de la app → Permisos de servicios en primer plano) y descríbelo con el [config plugin](#9-config-plugin).

## Solución de problemas

**La burbuja no aparece**

- Revisa `FloatingBubble.isAvailable` (`false` en Expo Go) y `hasOverlayPermission()`.
- Revisa `isEnabled()` y tu `showWhen`. Con `'background'` (por defecto), la burbuja solo aparece después de salir de la app.
- Revisa `isVisible()` para saber si está en pantalla ahora mismo.

**La burbuja desaparece sola**

- El usuario la tocó: por defecto se oculta hasta que tu app vuelva a segundo plano. Usa `hideOnPress: false` para mantenerla.
- El usuario la soltó sobre la ✕: escucha `onDismiss`.
- El usuario cerró tu app desde las apps recientes: la burbuja se va con ella.

**La app no se abre desde la burbuja o con `bringAppToForeground()`**

- Hace falta el permiso de superposición para abrir la app desde segundo plano.
- Algunas capas de fabricante (por ejemplo, Xiaomi MIUI / HyperOS) tienen un permiso extra, "Mostrar ventanas emergentes en segundo plano", que el usuario debe activar para tu app.

**Android cierra la app en segundo plano**

- Los ahorradores de batería agresivos de algunos dispositivos pueden detener apps incluso con un servicio en primer plano. Pide al usuario que excluya tu app de la optimización de batería.

**Mi icono personalizado no aparece**

- La burbuja mantiene el icono de tu app cuando la imagen no se puede cargar. Revisa la URL, la ruta del archivo o el nombre del recurso, y que la imagen pese menos de 5 MB.

## App de ejemplo

La carpeta [`example`](example) contiene una app que pide los permisos ("Mostrar sobre otras apps" y, en Android 13+, notificaciones) y permite probar todas las opciones: un interruptor "Show floating bubble", uno de icono personalizado, uno de "Hide on tap" y un selector de `showWhen`. Las [capturas de pantalla](#capturas-de-pantalla) se tomaron con ella.

```bash
cd example
npm install
npx expo run:android
```

## Desarrollo

```bash
npm install
npm run build          # TypeScript → build/
npm run build plugin   # config plugin → plugin/build/
CI=1 npm test          # Jest: API de JS y config plugin
npm run lint
```

Los tests nativos (Robolectric) se ejecutan a través de la app de ejemplo:

```bash
cd example && npx expo prebuild -p android
cd android && ./gradlew :expo-floating-bubble-overlay:testDebugUnitTest
```

## Licencia

[MIT](LICENSE) © Michael Valdiviezo
