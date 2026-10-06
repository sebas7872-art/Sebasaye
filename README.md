# Sanavera EQ 1.0.0

Proyecto Android nativo completo. Actualización de 0.5.4, `versionCode 12`.
Application ID y namespace: `app.ayelen.eqtest`. Android mínimo: 9 / API 28.

## Abrir y compilar

1. Abrir esta carpeta con Android Studio (compatible con AGP 8.7.3).
2. Seleccionar JDK 17 para Gradle; instalar Android SDK Platform 34 y Build Tools 35.0.0 desde SDK Manager.
3. Sincronizar Gradle. El Wrapper incluido descarga Gradle 8.9 con SHA-256 verificado; la primera compilación necesita Internet para las dependencias.
4. Compilar desde Android Studio o ejecutar `./gradlew assembleDebug` (`gradlew.bat assembleDebug` en Windows).
5. Pruebas automáticas: `./gradlew testDebugUnitTest`. Las pruebas de interfaz guardan capturas en `app/build/ui-screenshots`.

El proyecto usa interfaces Android nativas y no tiene dependencias de ejecución externas. Las dependencias JUnit/Robolectric sólo se usan para pruebas. `releases/Sanavera-EQ-1.0.0.apk` contiene la APK firmada entregada.

## Actualizaciones y firma

La APK entregada conserva el certificado propio de pruebas de 0.5.4; no es el debug.keystore predeterminado de Android Studio. Huella SHA-256 del certificado:

`04f9a7b51894e5a7f36bb2c2b6af80e070adaafbfb0e4b97e0564385b68dc4a8`

La clave privada **no se incluye**. Para futuras actualizaciones, conservar de forma segura el `test-release.p12` original, su alias y contraseñas. Usar “Generate Signed Bundle / APK” con ese mismo archivo, o definir fuera del repositorio las variables `SANAVERA_KEYSTORE`, `SANAVERA_STORE_PASSWORD`, `SANAVERA_KEY_ALIAS` y `SANAVERA_KEY_PASSWORD` y ejecutar `./gradlew assembleRelease`.

Sin esas variables, `assembleRelease` genera una APK sin firma. `assembleDebug` usa la firma local de desarrollo y no puede actualizar la instalación entregada con otro certificado. No desinstalar la app para actualizar si se desea conservar sus datos. Incrementar `versionCode` para futuras entregas y mantener el mismo application ID y certificado. No subir la clave ni las contraseñas a GitHub, incluso si el repositorio es privado.

## Cambios de esta versión

- Logo: se quitó el fondo claro del ImageView en tema oscuro; el PNG transparente original queda intacto.
- Simple: 3–10 bandas, frecuencias editables, orden automático, creación confirmada, eliminación de extras y ajustes Flat / eliminar agregadas / restablecer.
- La identidad de Graves/Medios/Agudos permanece estable; defaults 37 Hz, 1 kHz, 10 kHz. El diseño se adapta al ancho real sin desplazamiento horizontal.
- Persistencia v4 compatible con datos v1–v3 y presets existentes. Simple sigue siendo una capa de respuesta sobre el mismo motor nativo de 32 regiones; la receta interna de SURROUND no cambia.

Las pruebas automáticas con efectos nativos simulados validan estados, sesiones, persistencia y UI. No sustituyen una escucha física con Spotify, Bluetooth, Samsung o Motorola. La verificación acústica final debe realizarse en los dispositivos reales.

El proyecto entregado fue compilado con `assembleRelease`; sus 21 pruebas pasaron con `testDebugUnitTest`. Ver `VALIDACION.txt` y `releases/SHA256SUMS`.
