# FRANCA — Android Kotlin / Jetpack Compose

**Franca** es una app nativa Android de finanzas personales para registrar ingresos, gastos y transferencias.
La primera versión del repositorio es **0.2.24**, disponible como [versión de prueba para Android](https://github.com/Yhon-26/Franca_app/releases/tag/v0.2.24). Para probar la captura de pagos en el teléfono, consulta [PRUEBA_YAPE.md](PRUEBA_YAPE.md).

## Captura automática de pagos

Franca puede proponer movimientos a partir de avisos de **Yape recibido** y confirmaciones de **Yape enviado**. La lectura directa de pagos BCP sigue en pruebas con dispositivos físicos. Gmail está disponible como respaldo opcional y se puede desactivar desde Ajustes.

Cuando llega un aviso compatible de Yape o Franca encuentra un correo compatible de BCP, la app te notifica. Toca el aviso de Franca para abrir el movimiento con el monto propuesto. Puedes guardar un Yape recibido como ingreso, un pago BCP como gasto o cualquiera de los dos como transferencia entre tus cuentas, según corresponda. Al confirmar, el movimiento queda guardado desde esa pantalla.

Para los yapeos enviados, Franca analiza en el teléfono el texto que Android expone al servicio de accesibilidad, cuando cada usuario ha activado el servicio. El lector busca una confirmación de envío con monto en soles y número de operación y permite guardar como gasto o transferencia al tocar el aviso. El usuario confirmó la detección de tres envíos reales. BCP tiene un lector separado que consulta primero el origen accesible del evento y después las ventanas de la app, exige importe, fecha y hora, número de operación y confirmación saliente y prepara una propuesta pendiente. El diagnóstico diferencia el servicio activo del acceso efectivo al comprobante. Quedan pendientes la lectura real BCP en esta versión e identificar una fuente para BCP recibido.

## Probar pagos BCP por Gmail

1. En [Google Cloud](https://console.cloud.google.com/), crea o abre un proyecto para Franca y habilita la [API de Gmail](https://console.cloud.google.com/apis/library/gmail.googleapis.com).
2. En Google Auth Platform, configura **Branding** y **Audience** para pruebas externas y agrega la cuenta que recibe los correos de BCP como usuario de prueba. En **Data Access**, añade `https://www.googleapis.com/auth/gmail.readonly`.
3. En [Clients](https://console.cloud.google.com/auth/clients), configura el cliente **Android** con el paquete `com.grafito.finanzas` y la huella SHA-1 `CB:62:AA:FE:D1:07:D1:51:DA:E2:BE:BC:ED:4E:B9:6D:95:0A:FF:D5` del certificado que firma el APK privado 0.2.8. Para comprobar otro APK, ejecuta `apksigner verify --print-certs archivo.apk` y usa el SHA-1 del certificado (`certificate SHA-1 digest`). [Guía de Android](https://developer.android.com/identity/authorization#set_up_your_google_cloud_console_project)
4. En el teléfono, abre **Franca → Ajustes → BCP → Respaldo opcional → Configurar → Conectar Gmail** y autoriza en la pantalla de Google. **Buscar ahora** permite consultar las confirmaciones compatibles de los últimos siete días. Android programa las siguientes revisiones cada 15 minutos y puede retrasarlas.
5. Toca **Revisar y registrar** en un aviso de Franca o abre **Gastos por revisar** en Ajustes. Elige la cuenta y la categoría si corresponde, y confirma.

El piloto reconoce correos autenticados de BCP en soles, incluidos pagos a celular y con QR, cuando puede identificar una operación saliente, un importe, una fecha y hora y un número de operación. Franca usa la fecha y el número de operación para reconocer un pago que ya detectó o registró. **Desactivar correo** borra la conexión local y cancela las consultas; conserva los movimientos pendientes y el servicio de lectura directa. Puedes retirar el permiso de Google desde las [conexiones de tu cuenta de Google](https://myaccount.google.com/connections).

## Iniciar en Windows (Android Studio)

1. Instala **Android Studio** con Android SDK **API 37**. Gradle usa **JetBrains Runtime 21** para su daemon, según `gradle/gradle-daemon-jvm.properties`.
2. Clona el proyecto con `git clone https://github.com/Yhon-26/Franca_app.git`.
3. Ejecuta **`preparar-windows.bat`** si necesitas verificar o recuperar el JAR del wrapper. El script comprueba el SHA-256 del JAR 9.8.2 y el wrapper verifica por separado la distribución Gradle 9.9.0.
4. Abre Android Studio → **Open** → selecciona la carpeta `Franca_app`.
5. Espera a que termine **Gradle Sync** (requiere internet la primera vez). Acepta instalar SDK si te lo solicita.
6. Conecta un Android (API 26 o superior) con depuración USB o inicia un emulador. Pulsa **Run ▶**.
7. Para crear un APK de depuración: menú **Build → Build APK(s)**, o en consola de proyecto `gradlew.bat assembleDebug`. Ruta esperada: `app\build\outputs\apk\debug\app-debug.apk`.

El proyecto usa Kotlin integrado en Android Gradle Plugin. El wrapper fija la distribución de Gradle y su SHA-256 en `gradle/wrapper/gradle-wrapper.properties`.

## Trabajar por ramas

`main` es la base de las versiones publicadas. Crea cada actualización o rediseño desde una copia actual de `main`, en su propia rama.

```bash
git switch main
git pull --ff-only
git switch -c feature/nombre-del-cambio
```

Usa `fix/` para correcciones, `feature/` para funciones y `redesign/` para cambios de diseño. Al terminar la validación, sube la rama y abre un pull request hacia `main`.

## Qué está implementado

- **Interfaz.** Paleta clara y azul en Inicio, Registro y revisión de Yape. Las confirmaciones de guardado aparecen arriba y se cierran automáticamente.
- **Dashboard:** saldo total, resumen mensual, atajos favoritos, movimientos recientes.
- **Registro rápido:** monto como único dato escrito obligatorio, categoría opcional, fecha/hora automáticas y cuenta preseleccionada. Preferencia para abrir con el teclado numérico.
- **Registro híbrido:** atajos que abren un gasto prellenado para confirmarlo, sugerencia de categoría según importes repetidos en el historial y revisión de movimientos detectados por avisos de Yape o correos de BCP.
- **Cuentas:** crear cuentas manuales con saldos iniciales, consultar balance por cuenta, transferir sin duplicar el gasto, eliminar solo cuentas sin movimientos.
- **Movimientos y Reportes:** las pestañas muestran «En desarrollo». Inicio permite consultar movimientos recientes, ingresos y gastos del mes.
- **Persistencia:** Room + SQLite; todas las cantidades monetarias se guardan en **centavos enteros**.
- **Preferencias:** DataStore para modo de apertura.
- **Cuenta predeterminada:** selección desde Ajustes para nuevos gastos e ingresos; cada atajo puede usar su propia cuenta y la muestra en Inicio.
- **Protección contra repetición:** guardado deshabilitado mientras se escribe y protección de 1,5 segundos por atajo; la creación de movimiento y atajo es atómica.
- **Respaldo/restauración:** Ajustes → Copias de seguridad; archivo `.grafito` cifrado con contraseña, vista previa y reemplazo atómico confirmado. Incluye cuentas, categorías, movimientos, atajos y cuenta predeterminada.
- **Migraciones Room 1 → 7** conservan los registros existentes. Los esquemas se exportan en `app/schemas`.
- **Sin datos financieros de demostración:** crea solo la cuenta «Efectivo» con S/ 0 y las categorías por defecto. Los favoritos se crean al registrar gastos.

## Arquitectura

```text
app/src/main/java/com/grafito/finanzas/
├── MainActivity.kt                # navegación y acciones de interfaz
├── data/
│   ├── Entities.kt               # tablas Room
│   ├── FinanceDao.kt             # consultas suspend y Flows
│   ├── FinanceDatabase.kt        # base local
│   └── UserPreferences.kt        # DataStore
├── model/
│   ├── FinanceMath.kt            # saldos y rangos temporales
│   ├── Money.kt                  # importe seguro en centavos
│   └── FinanceViewModel.kt       # operaciones y estado reactivo
└── ui/
    ├── theme/GrafitoTheme.kt
    ├── components/FinanceComponents.kt
    └── screens/                    # inicio, registro, historial, cuentas, reportes, ajustes
```

## Privacidad y límites actuales

- El piloto de Yape procesa avisos de dinero recibido en el teléfono después de activarlo y conceder acceso de Android. Si conectas Gmail, Franca usa internet para consultar la API de Google y guarda los datos del pago en el teléfono. Los correos completos se procesan en memoria. Los movimientos se registran cuando los confirmas.
- Room guarda los datos en el sandbox privado del dispositivo, **sin cifrado adicional de base de datos ni bloqueo PIN**. No es adecuado para almacenar claves o datos bancarios sensibles.
- Backup automático deshabilitado, con exclusiones explícitas para nube y transferencia de dispositivo. **Desinstalar elimina la base local**: guarda primero una copia cifrada fuera del almacenamiento privado de Grafito y conserva su contraseña. El modo rápido/resumen es una preferencia del dispositivo y no se exporta.
- Las copias usan AES-256-GCM, PBKDF2-HMAC-SHA256 (600.000 iteraciones), sal y nonce aleatorios. Límite: 16 MB y 100.000 movimientos; restaurar reemplaza los datos, no mezcla historiales. Una contraseña olvidada no se puede recuperar.
- Aún están pendientes los widgets, el registro por voz, las cuentas multiusuario, la sincronización en nube, los presupuestos, los recordatorios y la seguridad biométrica/PIN.
- Pruebas: `gradlew.bat testValidationUnitTest connectedValidationAndroidTest assembleDebug lintValidation`. El paquete `.validation` mantiene sus datos de prueba separados de la instalación normal.
- Las bibliotecas ARM64/x86_64 pasan LOAD/RELRO y el APK pasa ZIP alignment de 16 KB. Arranque comprobado en el emulador API 37 con páginas de 16 KB y `pageSizeCompat=0`.
- **En esta versión no utilizar datos financieros reales hasta validar instalación, estabilidad y recuperación de información.**

## Preparar un APK de prueba en Windows

Desde la raíz del proyecto, ejecuta `.\scripts\validar-entrega.ps1`. Requiere Python 3 y Android SDK Build-Tools 35.0.0 o posterior. El comando ejecuta las pruebas unitarias y Lint, compila el APK de depuración y verifica su firma y alineación de 16 KB. El APK validado queda en `build/entregas` junto con su SHA-256. El identificador de la compilación figura en el nombre del APK y en Ajustes.

Para ejecutar también las pruebas instrumentadas, usa `.\scripts\validar-entrega.ps1 -EmulatorSerial emulator-5554` con un emulador de pruebas iniciado. La variante `.validation` usa datos separados de la instalación habitual. Usa `-Offline` si todas las dependencias ya están en la caché local.

## Tecnologías del proyecto

- Kotlin integrado en Android Gradle Plugin · Compose Compiler 2.3.21
- Android Gradle Plugin 9.5.1 · Gradle 9.9.0 · compileSdk 37 · targetSdk 35
- Compose BOM 2026.09.00 · Material 3 · Activity 1.13.0
- Room 2.8.5 · KSP 2.3.10 · ViewModel/Lifecycle 2.11.0 · DataStore 1.3.0 · graphics-path 1.1.0
- WorkManager 2.12.0 · Kotlin Coroutines 1.11.0
- La migración de targetSdk a Android 16/17 requiere validar sus cambios de comportamiento antes de activarlos.
- Mínimo Android 8.0 (API 26)

## Próximos pasos de ingeniería

1. Validar en teléfono físico y Android API 26/otras versiones; probar el selector real de archivos y la recuperación en otro dispositivo.
2. Completar revisión TalkBack, teclado numérico según IME, orientación, tamaños de pantalla y uso prolongado.
3. Evaluar PIN/biometría y cifrado adicional de la base local antes de ampliar distribución.
4. Medir el tiempo de registro con personas reales: meta inicial 3 segundos para gasto habitual.
5. Widgets, accesos rápidos, categorías configurables, sugerencias basadas en hábitos y recordatorios opcionales.
