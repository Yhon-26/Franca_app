# Franca — splash animado nativo

La animación nativa se basa en un prototipo de Figma Make. Los recursos que necesita Android están incluidos en el proyecto.

Implementación: `FrancaSplash.kt`, `SplashMotion.kt` y `SplashArtwork.kt`. Compose + Canvas y capas gráficas nativas; no utiliza WebView, vídeo, conexión a internet ni fuentes remotas.

## Secuencia de la referencia

- Fondo `#1A1A1D`, arco/nombre `#F2F2F5`, nudos `#89D9B1`, lema `#A9A9A6`.
- Nodo central desde 120 ms; arco dibujado desde el centro superior en dos mitades desde 260 ms.
- Trazos de la F desde 820/860/1020 ms; seis nudos con los retardos originales entre 1020 y 1310 ms.
- Letras desde 1500 ms, separadas por 75 ms: giro X desde −78°, subida del 42 % de la altura y aparición progresiva. Desenfoque nativo en Android 12/API 31 y superiores; en API 26–30 se conserva giro, desplazamiento y opacidad sin blur.
- Lema desde 2300 ms durante 900 ms.
- Resortes del export original: simulación de 600 Hz, muestreo en 64 intervalos; rigidez/amortiguación 90/18, 320/14 y 140/13.
- Geometrías originales del nombre y lema copiadas de `paths.ts`; disposición `600 × 660`, ancho `min(46vw, 52vh, 340dp)`, como en Make.

La adaptación añade una pausa final de 150 ms y salida de 200 ms: **3,55 segundos de animación**, además del arranque del proceso Android. Al terminar, se abre el modo habitual del usuario. La base y las preferencias se inicializan mientras se presenta el splash. `rememberSaveable` evita volver a mostrarlo al recrear la actividad o regresar a la app; un nuevo arranque del proceso vuelve a reproducirlo.

Android 12+ conserva su pantalla de inicio obligatoria con fondo uniforme y sin segundo logo estático, para enlazar con la secuencia Compose. Se respeta la escala de duración del sistema; con escala cero, se completa sin espera.

## Validación local

Versión **0.2.2**, código 4. `assembleDebug connectedValidationAndroidTest`: **BUILD SUCCESSFUL**, 9 pruebas instrumentadas aprobadas en el paquete separado `.validation`, incluida conservación de navegación/borrador al recrear la actividad.

Instalación con `adb install -r` en `emulator-5554` (Android 17/API 37). Grabación completa inspeccionada por fotogramas: arco, cuerdas, nudos, letras, lema, salida y modo rápido. Prueba adicional con `animator_duration_scale=0`: la app queda abierta sin splash visible; se restauró el valor anterior del emulador. No se han validado rendimiento físico ni el fallback sin blur en API 26–30.
