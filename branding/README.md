# Franca — identidad Android

Los recursos Android se generaron a partir del prototipo visual de Franca. Para regenerarlos, coloca el archivo fuente `Graphite.dc.html` en la carpeta local `branding/franca-reference/`.

- Marca visible: **Franca**; logotipo en minúsculas **franca**.
- Lema: **Cuentas claras, hogar firme**.
- Símbolo: arco que abraza una F de cuerdas con nudos. Geometría extraída del SVG original.
- Aplicación solicitada: nombre y arco blancos `#F2F2F5`, lema gris y nudos verdes `#89D9B1` sobre fondo `#0E0E10`. El usuario confirmó conservar las bolitas verdes por su significado de conteo inca. Se excluye únicamente la variante con fondo verde. Los iconos temáticos del sistema conservan la versión monocromática por su función Android.
- Nombre dibujado con **Fraunces 600**, la familia especificada en el HTML, convertido a trazados vectoriales con kerning y espaciado de la referencia. No hay descargas de fuentes durante el uso de la app. Archivo original y licencia SIL OFL en `font-source/`; fuente obtenida del repositorio oficial `google/fonts`.

## Recursos

- `app/src/main/res/drawable/ic_franca.xml`: símbolo para la interfaz.
- `app/src/main/res/drawable/franca_wordmark.xml`: palabra «franca» vectorial.
- `app/src/main/res/drawable/ic_franca_launcher.xml`: primer plano del icono adaptable, dentro del área segura.
- `app/src/main/res/drawable/ic_franca_monochrome.xml`: variante para iconos temáticos Android.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml`: iconos adaptable y redondo sobre fondo Grafito.
- `FrancaBrand.kt`: composición reutilizada en Inicio y Ajustes, con etiqueta accesible.

Generador: `python branding/generate_franca_assets.py` (dependencias de desarrollo: `fonttools`, `uharfbuzz`). Los vectores generados están incluidos; estas herramientas no son necesarias para compilar ni ejecutar Android.

## Continuidad de datos

Se conserva `applicationId=com.grafito.finanzas`, la base Room, las preferencias y la firma de depuración existente para actualizar la instalación sin crear otra app. El identificador interno `GRAFITO1` y la extensión `.grafito` del respaldo se mantienen para seguir leyendo copias anteriores; los archivos nuevos se proponen como `Franca-fecha.grafito`. Estos identificadores técnicos no son el nombre visible de la marca.

## Validación de esta entrega

Versión 0.2.1, código 3. `assembleDebug lintValidation testValidationUnitTest`: 7 pruebas unitarias aprobadas y Lint con 0 errores y 8 advertencias. Se verificaron Inicio y el icono de Franca en el lanzador del emulador API 37.
