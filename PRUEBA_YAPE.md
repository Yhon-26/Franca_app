# Probar la captura de Yape y BCP en Franca 0.2.24

Franca **0.2.24** es una versión de prueba. Si tienes la versión anterior instalada con la misma firma, Android debería ofrecer **Actualizar**. Si Android rechaza la actualización por incompatibilidad, haz una copia desde Franca antes de considerar desinstalar, porque desinstalar borra sus datos locales.

En Xiaomi y Redmi, activa el inicio automático de Franca y selecciona **Sin restricciones** en sus ajustes de batería. En el Redmi revisado, ambos lectores estaban desconectados y el inicio automático estaba bloqueado.

El lector existente de Yape enviado se declara como herramienta de accesibilidad. En el Redmi con Android 16, Yape 3.70.0 marca su ventana como contenido sensible. El lector procesa las ventanas de Yape y omite campos editables y de contraseña. El lector nuevo de BCP es independiente y respeta el contenido protegido de Android. [Contenido sensible para accesibilidad en Android](https://developer.android.com/reference/android/view/View#isAccessibilityDataSensitive())

## Configurar los avisos emergentes

En **Franca → Ajustes → Notificaciones** se abre la configuración de Android para Franca. En Redmi, activa **Notificaciones flotantes** para la app y para las categorías **Yapeos enviados**, **Ingresos de Yape** y **Gastos BCP** que aparezcan.

Los canales nuevos usan importancia alta. Los canales que ya existían conservan las preferencias elegidas en Android. En el Redmi revisado se activaron los avisos flotantes de Yape enviado y BCP. Los próximos avisos muestran el importe en el título y el botón **Registrar**, que abre la revisión del movimiento.

## Probar un pago enviado desde BCP

1. Actualiza desde **Franca-0.2.22-BCP-directo.apk**, abre **Franca → Ajustes → BCP** y activa **Franca · Pagos BCP** desde **Abrir Accesibilidad de Android**. Permite los avisos de Franca si faltan. La lectura directa no requiere Gmail conectado.
2. Mantén desactivada la depuración USB. Abre en BCP el comprobante existente que muestra **¡Operación exitosa!** y déjalo visible unos segundos. Esta prueba usa una operación anterior y conserva su fecha original.
3. Vuelve a **Franca → Ajustes → BCP**. **Servicio de BCP activo** indica que Android inició el servicio y es independiente del resultado de lectura. Comprueba la hora de la última comprobación y los campos reconocidos: importe, fecha y hora, número de operación y envío confirmado. Puedes reportar esos indicadores sin compartir datos bancarios.
4. Si aparece el aviso, toca **Registrar** para elegir cómo guardar el movimiento. Si el correo de esa operación ya había sido reconocido, puede aparecer **Esta operación BCP ya aparece en Franca**. Abrir de nuevo el comprobante conserva una sola propuesta.

La captura de BCP se procesa en el teléfono. Se comprueba el título de confirmación antes de extraer el cuerpo y se omiten campos editables y contraseñas. Además de la ventana activa y la del evento, el lector revisa las otras ventanas de aplicación expuestas por Android y filtra el contenido por BCP. Si BCP o Android protege el texto, el panel informa que la confirmación todavía no es legible. En 0.2.19, el usuario reportó **Ventana de BCP sin acceso en el último intento** incluso con la depuración USB apagada. En la prueba posterior de 0.2.20, el usuario realizó un pago y la captura siguió indicando que Android no entregó una ventana, aunque el lector estaba conectado. **La captura directa de BCP no funciona todavía en este Redmi.** El correo sigue disponible como fuente alternativa. Para revisar un pago ya realizado, usa **Buscar ahora** con Gmail conectado.

## Estado de la lectura directa

En **Ver estado de lectura** se indica si Android entregó un origen de BCP, una ventana consultable y una confirmación accesible, si se leyó desde el evento y si una consulta fue denegada. El diagnóstico guarda indicadores y la hora, sin guardar el texto bancario. Si no hay acceso, reporta esos indicadores; no es necesario hacer otro pago.

La versión 0.2.22 intenta primero el origen accesible del evento de BCP y sus padres del mismo paquete, omitiendo fuentes editables y contraseñas. Si logra leer los cuatro campos de una confirmación, prepara el aviso sin depender de la consulta de ventanas. Si falta información, consulta las ventanas normales y conserva el mismo parser y la misma protección contra duplicados. No se fuerza el acceso a contenido protegido por Android.

## Respaldo opcional por correo

El correo está en una tarjeta secundaria cerrada por defecto. **Configurar** muestra la autorización de Gmail y **Buscar ahora**. **Desactivar correo** está visible cuando hay una conexión y borra esa conexión local, cancela las revisiones pendientes y conserva el lector BCP y los movimientos existentes. Abrir BCP ya no programa búsquedas de correo.

En cada teléfono de la familia deben autorizarse los accesos de lectura y los avisos de Franca. Gmail solo se autoriza si se desea el respaldo por correo. La validación en emuladores no demuestra el comportamiento de las apps bancarias y las restricciones de cada fabricante. Conserva el estado y la hora de lectura al probar un equipo real.

## Probar un Yape enviado

1. Abre **Franca → Ajustes → Yape → Yape enviado** y toca **Abrir Accesibilidad de Android**. Activa el servicio **Franca · Yape enviado**. Si Android indica que el servicio no funciona con el interruptor encendido, apágalo y vuelve a encenderlo. En Franca debe aparecer **Lector conectado**. Si Android pide permiso de notificaciones para Franca, concédelo para revisar desde el aviso.
2. Primero abre Yape, espera unos segundos en su pantalla de inicio y vuelve a **Franca → Ajustes → Yape**. Si dice **Yape visible**, Franca encontró texto en esa pantalla. Si el estado indica **Ventana de Yape sin acceso** o **sin texto legible**, basta con reportar ese mensaje. Puedes hacer esta comprobación sin pagar.
3. En tu próximo pago habitual, deja visible unos segundos la confirmación de Yape. Si Franca muestra **Yape enviado detectado**, toca el aviso y comprueba el monto y la cuenta. Guarda como gasto o usa **Entre mis cuentas** si el pago fue una transferencia propia.
4. Si no aparece el aviso, revisa el estado del lector. En **Yapeos por revisar** puedes encontrar la detección si los avisos de Franca estaban desactivados. Para reportar el resultado basta el estado y la versión de Android.

## Probar un Yape recibido

## Preparar el teléfono

1. Pasa el APK al teléfono y ábrelo desde Archivos. Si Android lo solicita, permite la instalación desde esa app para instalar este APK.
2. Abre Franca. Si aparece el registro rápido, ciérralo y entra a **Ajustes → Yape → Yape recibido**.
3. Activa el interruptor y toca **Conceder acceso en Android**. Busca **Franca** y habilita el acceso a notificaciones. Android concede acceso a las notificaciones del teléfono. El lector de Franca filtra por la app de Yape Perú y procesa esos avisos localmente.
4. Si el teléfono muestra «Ajuste restringido», abre **Ajustes de Android → Apps → Franca → Más → Permitir ajustes restringidos**. Regresa al acceso a notificaciones y actívalo. El menú depende de la versión y el fabricante. [Ayuda de Android](https://support.google.com/android/answer/12623953?hl=es)
5. Regresa a Franca. Busca el estado **Activo. Esperando avisos nuevos de Yape.** Comprueba que Yape tenga sus notificaciones habilitadas y deja ambas apps en segundo plano para la prueba.
6. Si Franca muestra **Permitir avisos de Franca**, toca el botón y acepta. Este permiso permite que Franca muestre su propio aviso cuando detecta un ingreso.

## Recibir y revisar

1. Cuando recibas tu próximo yapeo habitual con la captura activada, comprueba que el movimiento aparezca en **Movimientos** de Yape. [Cómo verificar un pago recibido](https://www.yape.com.pe/preguntas-frecuentes/yape-negocios/como-puedo-verificar-que-he-recibido-un-pago-por-yape)
2. En el aviso de Franca toca **Revisar y registrar**. Comprueba el monto, elige la cuenta donde recibiste el dinero y, si quieres, selecciona una categoría de ingreso. Si ya tienes una sola cuenta llamada Yape, aparecerá preseleccionada. Si cierras el aviso, puedes volver a abrir el ingreso en **Ajustes → Yape → Yapeos por revisar**.
3. Pulsa **Guardar ingreso** y verifica el movimiento y el saldo en Inicio. También puedes descartar el aviso desde la revisión.

## Qué observar si falla

- **Android aún no ha conectado el lector de Yape.** Toca **Reconectar lector**. Si el estado persiste, entra a **Revisar acceso de Android**, desactiva y vuelve a activar el acceso de Franca y regresa al panel. Al conectarse, Franca revisa los avisos de Yape que Android todavía mantiene activos, desde que habilitaste la captura.
- **Falta conceder acceso.** Revisa el acceso en Android y vuelve al panel.
- **Activo**, pero sin «Último aviso». Confirma que el aviso nuevo de Yape apareció en el teléfono tras activar la captura.
- **Aviso recibido. Sin un ingreso con monto reconocible.** Android entregó un aviso, pero el texto disponible no coincidió con un ingreso con monto. Android puede ocultar contenido de avisos que contienen códigos. Para revisar el piloto basta con anotar el estado y la versión de Android. [Cambios de Android para notificaciones sensibles](https://developer.android.com/about/versions/15/behavior-changes-all?hl=es)

La prueba local usa notificaciones sintéticas del emulador. Franca guarda el monto y la hora del aviso pendiente con un identificador para reconocer repeticiones del mismo aviso. Al confirmar o descartar, borra el monto de la lista de avisos. El historial financiero conserva los ingresos que confirmes.

## Resultado en teléfono físico

En pruebas anteriores se verificó la detección de un ingreso de Yape y la revisión de un comprobante de envío existente. Falta repetir ambos recorridos en un teléfono físico con esta compilación.
