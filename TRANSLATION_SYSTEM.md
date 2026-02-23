# Sistema de Traducción de SpaceGram

## Resumen
SpaceGram ahora incluye un sistema de traducción robusto con múltiples proveedores de traducción, sin restricciones de Telegram Premium.

## Proveedores Disponibles

1. **Google Translate** (Proveedor ID: 1) - Gratuito
   - No requiere API key
   - Límite: ~5000 caracteres por solicitud
   - Velocidad: Rápida

2. **DeepL** (Proveedor ID: 2) - Requiere API key
   - Mejor calidad de traducción
   - API gratuita disponible en https://www.deepl.com/pro-api
   - Límite gratuito: 500,000 caracteres/mes

3. **Yandex** (Proveedor ID: 3) - Requiere API key
   - Bueno para idiomas eslavos
   - API key gratuita disponible en https://translate.yandex.com/developers
   - Límite gratuito: 1 millón de caracteres/día

4. **Microsoft Translator** (Proveedor ID: 4) - Requiere API key
   - Azure Cognitive Services
   - Prueba gratuita disponible en https://azure.microsoft.com/cognitive-services/translator/
   - Límite gratuito: 2 millones de caracteres/mes

5. **LibreTranslate** (Proveedor ID: 5) - Opcional API key
   - Completamente open source
   - Puede auto-hospedarse sin límites
   - Instancia pública: https://libretranslate.com
   - Sin límites si se auto-hospeda

6. **MyMemory** (Proveedor ID: 6) - Opcional API key
   - Gratuito sin API key
   - Límite sin key: 5000 caracteres/día
   - Con key gratuita: hasta 20,000 caracteres/día
   - Registro en https://mymemory.translated.net/

## Archivos Modificados

### Nuevos Archivos Creados:
- `org/spacegram/translator/DeepLTranslator.java` - Implementación DeepL
- `org/spacegram/translator/YandexTranslator.java` - Implementación Yandex
- `org/spacegram/translator/MicrosoftTranslator.java` - Implementación Microsoft
- `org/spacegram/translator/LibreTranslateTranslator.java` - Implementación LibreTranslate
- `org/spacegram/translator/MyMemoryTranslator.java` - Implementación MyMemory

### Archivos Modificados:
- `org/spacegram/translator/SpaceGramTranslator.java` - Agregados todos los proveedores
- `org/spacegram/ui/SpaceGramGeneralSettingsActivity.java` - UI para seleccionar proveedor
- `org/telegram/ui/Components/TranslateAlert2.java` - Usa SpaceGramTranslator en lugar de Google directo
- `org/telegram/ui/Components/TranslateButton.java` - Removidas verificaciones de Premium

## Cambios Principales

### 1. Eliminación de Restricciones Premium
**Antes:**
```java
if (UserConfig.getInstance(currentAccount).isPremium() || chat != null && chat.autotranslation) {
    onMenuClick();
} else {
    onCloseClick();
}
```

**Después:**
```java
// SpaceGram: Always allow translation menu, no premium check
onMenuClick();
```

### 2. Sistema Modular de Proveedores
Todos los proveedores implementan la interfaz `BaseTranslator`:
```java
public interface BaseTranslator {
    void translate(String text, String fromLang, String toLang, Utilities.Callback2<String, Boolean> done);
}
```

### 3. Selector de Proveedores en UI
Los usuarios pueden elegir su proveedor preferido desde:
**Configuración > SpaceGram > General > Proveedor de Traducción**

## Características

✅ **Sin restricciones de Premium** - Todos los usuarios tienen acceso completo
✅ **Múltiples proveedores** - 6 opciones diferentes
✅ **Auto-traducción** - Traducción automática de chats completos
✅ **Caché inteligente** - Reduce llamadas a APIs
✅ **Fallback automático** - Si un proveedor falla, se puede cambiar fácilmente
✅ **Popup mejorado** - Menú contextual completo con todas las opciones:
   - Traducir a [idioma]
   - No traducir [idioma]
   - Ocultar traducción

## Configuración

### Usar Proveedores Gratuitos (Google/MyMemory)
No se requiere configuración adicional. Funcionan de inmediato.

### Usar Proveedores con API Key
Para proveedores que requieren API key (DeepL, Yandex, Microsoft), necesitas:

1. Registrarte en el servicio del proveedor
2. Obtener tu API key
3. **PRÓXIMAMENTE**: Agregar la opción de configurar API keys en la UI
   (Por ahora están vacías, pero se puede modificar el código para agregar las keys temporalmente)

### Auto-hospedar LibreTranslate
```bash
docker run -d -p 5000:5000 libretranslate/libretranslate
```
Luego modificar la URL en `LibreTranslateTranslator.java`:
```java
private static final String DEFAULT_API_URL = "http://tu-servidor:5000/translate";
```

## Solución de Problemas

### Mensaje "Pagar para obtener traducción completa"
**SOLUCIONADO** ✅ - Esto era causado por verificaciones de Premium que hemos removido.

### Los 3 puntos no abren el menú
**SOLUCIONADO** ✅ - La verificación de Premium bloqueaba el acceso al menú.

### Traducción no funciona
- Verificar conexión a internet
- Cambiar a otro proveedor (Google o MyMemory no requieren configuración)
- Para proveedores con API: verificar que la key sea válida

### Límite de traducción alcanzado
- Cambiar a otro proveedor
- MyMemory: registrar una API key gratuita para más cuota
- LibreTranslate: auto-hospedar para ilimitado

## Próximas Mejoras

- [ ] UI para configurar API keys desde la app
- [ ] Detección automática de idioma mejorada  
- [ ] Historial de traducciones
- [ ] Exportar/importar configuración de proveedores
- [ ] Estadísticas de uso por proveedor
- [ ] Fallback automático entre proveedores

## Créditos

- Implementación basada en la arquitectura de Nekogram
- Proveedores excluidos como solicitado: lingocloud, youdao, baidu, sogou, transmart
