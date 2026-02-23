# Compilar y Probar el Sistema de Traducción

## Pasos para Compilar

### 1. Limpiar el proyecto
```bash
./gradlew clean
```

### 2. Compilar la aplicación
```bash
./gradlew assembleDebug
```

O para compilar el APK de release:
```bash
./gradlew assembleRelease
```

### 3. Verificar que no hay errores
Durante la compilación, el sistema verificará automáticamente:
- ✅ Todas las clases de traducción están importadas correctamente
- ✅ No hay referencias a verificaciones de Premium
- ✅ Los proveedores están registrados en SpaceGramTranslator

## Puntos de Verificación Manual

### Antes de compilar, verifica:

1. **Imports correctos en TranslateAlert2.java**
   ```java
   import org.spacegram.translator.SpaceGramTranslator;
   ```

2. **No hay verificaciones de Premium en TranslateButton.java**
   Buscar que NO aparezca:
   ```java
   UserConfig.getInstance(currentAccount).isPremium()
   ```

3. **SpaceGramTranslator tiene todos los proveedores**
   ```java
   providers.put(PROVIDER_GOOGLE, new GoogleTranslator());
   providers.put(PROVIDER_DEEPL, new DeepLTranslator());
   providers.put(PROVIDER_YANDEX, new YandexTranslator());
   providers.put(PROVIDER_MICROSOFT, new MicrosoftTranslator());
   providers.put(PROVIDER_LIBRETRANSLATE, new LibreTranslateTranslator());
   providers.put(PROVIDER_MYMEMORY, new MyMemoryTranslator());
   ```

4. **Selector de proveedores en UI**
   Verificar que `SpaceGramGeneralSettingsActivity.java` tiene el método:
   ```java
   private void showProviderSelector()
   ```

## Probar la Funcionalidad

### 1. Acceder a Configuración de Traducción
1. Abrir SpaceGram
2. Ir a **Configuración**
3. Ir a **SpaceGram**
4. Ir a **General**
5. Buscar la sección "Traductor"

### 2. Probar Selector de Proveedores
1. Tocar en "Proveedor de Traducción"
2. Debería aparecer un diálogo con 6 opciones:
   - Google Translate
   - DeepL
   - Yandex
   - Microsoft Translator
   - LibreTranslate
   - MyMemory
3. Seleccionar uno y verificar que se guarda

### 3. Probar Traducción en Chat
1. Abrir cualquier chat con mensajes en otro idioma
2. Tocar un mensaje
3. Debería aparecer el botón "Traducir"
4. NO debe aparecer mensaje de "pagar para traducción completa"
5. Tocar "Traducir" y verificar que traduce

### 4. Probar Menú Contextual (los 3 puntos)
1. Después de traducir, tocar los 3 puntos al lado del botón de traducción
2. Debería abrir un menú con opciones:
   - **Traducir a:** [idioma seleccionado]
   - **No traducir:** [idioma detectado]
   - **Ocultar:** Oculta la opción de traducción
3. Todas las opciones deben funcionar sin pedir Premium

### 5. Probar Auto-Translate
1. En Configuración > SpaceGram > General
2. Activar "Auto-Translate"
3. Abrir un chat con mensajes en otro idioma
4. Todos los mensajes deben traducirse automáticamente

## Problemas Comunes y Soluciones

### Error de compilación: "cannot find symbol SpaceGramTranslator"
**Solución:** Verificar que el archivo está en:
```
TMessagesProj/src/main/java/org/spacegram/translator/SpaceGramTranslator.java
```

### Error: "package org.spacegram.translator does not exist"
**Solución:** 
1. Hacer `./gradlew clean`
2. Sync del proyecto en Android Studio
3. Rebuild

### APK se instala pero no aparecen los nuevos proveedores
**Solución:** 
1. Desinstalar completamente la app anterior
2. Limpiar caché de Gradle: `./gradlew clean cleanBuildCache`
3. Reinstalar

### Traducción falla con error de red
**Posibles causas:**
- Sin conexión a internet
- Firewall bloqueando las APIs de traducción
- API key inválida (para DeepL, Yandex, Microsoft)

**Solución:**
1. Verificar conexión
2. Cambiar a Google Translate o MyMemory (no requieren API key)
3. Verificar logs: `adb logcat | grep -i translate`

## Logs de Depuración

Para ver logs de traducción en tiempo real:
```bash
adb logcat | grep -E "SpaceGramTranslator|TranslateAlert2|GoogleTranslator|DeepLTranslator"
```

Logs útiles:
- "Translation failed" - Indica error en la traducción
- "Translation successful" - Traducción completada
- "Provider: [nombre]" - Muestra qué proveedor se está usando

## Verificación de Código

### Comando para verificar que no hay Premium checks:
```bash
grep -r "isPremium" TMessagesProj/src/main/java/org/telegram/ui/Components/TranslateButton.java
```
**Resultado esperado:** Ninguna coincidencia

### Comando para verificar imports de SpaceGramTranslator:
```bash
grep -r "import org.spacegram.translator.SpaceGramTranslator" TMessagesProj/src/main/java/
```
**Resultado esperado:**
- TranslateAlert2.java
- SpaceGramGeneralSettingsActivity.java

## Testing Automatizado (Futuro)

Para agregar tests unitarios más adelante:
```java
@Test
public void testAllProvidersAvailable() {
    int[] ids = SpaceGramTranslator.getAllProviderIds();
    assertEquals(6, ids.length);
}

@Test  
public void testGoogleTranslatorWorks() {
    GoogleTranslator translator = new GoogleTranslator();
    // Mock network call
    translator.translate("Hello", "en", "es", (result, error) -> {
        assertNotNull(result);
        assertFalse(error);
    });
}
```

## Checklist Final

Antes de considerar la tarea completada:

- [ ] Compilación exitosa sin errores
- [ ] Compilación sin warnings sobre clases faltantes
- [ ] APK se instala correctamente
- [ ] Selector de proveedores muestra 6 opciones
- [ ] Traducción funciona con al menos un proveedor (Google)
- [ ] Los 3 puntos abren el menú contextual
- [ ] Menú muestra todas las opciones sin pedir Premium
- [ ] Auto-translate se puede activar/desactivar
- [ ] No aparecen mensajes de pago de Telegram
- [ ] Logs no muestran errores críticos

¡Listo para probar! 🚀
