cmake_minimum_required(VERSION 3.22.1)
project("tdlib_integration") # Nombre del proyecto CMake

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configuración para diferentes arquitecturas
# La variable ANDROID_ABI es establecida por el sistema de build de Android
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(TD_ARCH "arm64")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(TD_ARCH "arm") # TDLib podría usar "armeabi-v7a" o "arm" como nombre de directorio
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(TD_ARCH "x64")
elseif(ANDROID_ABI STREQUAL "x86")
    set(TD_ARCH "x86")
else()
    message(FATAL_ERROR "Unsupported target architecture: ${ANDROID_ABI}")
endif()

# Ruta a las bibliotecas y cabeceras precompiladas de TDLib
# Asume que existe una carpeta 'td' en la raíz del módulo (TMessagesProj/td)
set(TD_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/td") # CMAKE_CURRENT_SOURCE_DIR es el directorio de este CMakeLists.txt
set(TD_INCLUDE_DIR "${TD_ROOT}/include")
set(TD_LIB_DIR "${TD_ROOT}/lib/${TD_ARCH}") # Las bibliotecas precompiladas deben estar en td/lib/arm64, td/lib/arm, etc.

# Verificar que los directorios de TDLib existan (conceptual, la compilación fallaría aquí)
if(NOT EXISTS ${TD_INCLUDE_DIR})
    message(WARNING "TDLib include directory not found: ${TD_INCLUDE_DIR}")
endif()
if(NOT EXISTS ${TD_LIB_DIR})
    message(WARNING "TDLib library directory for ${TD_ARCH} not found: ${TD_LIB_DIR}")
endif()

# Configurar incluir directorios
include_directories(
    ${TD_INCLUDE_DIR}
    # Ruta a las cabeceras de nuestro código JNI si están separadas, aunque en este caso están junto al cpp
    src/main/cpp/tdlib
)

# Agregar biblioteca tdjson (la interfaz JSON de TDLib)
# Esta es la biblioteca principal que se usa para la interacción JNI vía JSON.
add_library(tdjson SHARED IMPORTED) # Cambiado de tdjsonandroid para posible consistencia con nombres comunes de TDLib
set_target_properties(tdjson PROPERTIES IMPORTED_LOCATION
    "${TD_LIB_DIR}/libtdjson.so") # Asumiendo que la biblioteca se llama libtdjson.so

# Crear nuestra biblioteca JNI (la que contiene td_jni.cpp y native-lib.cpp)
add_library(tdlib_jni_bridge SHARED # Nombre único para nuestra biblioteca JNI
    src/main/cpp/tdlib/native-lib.cpp
    src/main/cpp/tdlib/td_jni.cpp
)

# Vincular bibliotecas
target_link_libraries(tdlib_jni_bridge
    tdjson      # Vincular contra la biblioteca TDLib JSON
    log         # Para __android_log_print
    android     # Para otras APIs de Android si fueran necesarias
)
