include_directories(../include)

if(NOT OPENSSL_NO_ASM)
  if(UNIX)
    if (${ARCH} STREQUAL "aarch64")
      # The "armx" Perl scripts look for "64" in the style argument
      # in order to decide whether to generate 32- or 64-bit asm.
      if (APPLE)
        set(PERLASM_STYLE ios64)
      else()
        set(PERLASM_STYLE linux64)
      endif()
    elseif (${ARCH} STREQUAL "arm")
      if (APPLE)
        set(PERLASM_STYLE ios32)
      else()
        set(PERLASM_STYLE linux32)
      endif()
    elseif (${ARCH} STREQUAL "ppc64le")
      set(PERLASM_STYLE linux64le)
    else()
      if (${ARCH} STREQUAL "x86")
        set(PERLASM_FLAGS "-fPIC -DOPENSSL_IA32_SSE2")
      endif()
      if (APPLE)
        set(PERLASM_STYLE macosx)
      else()
        set(PERLASM_STYLE elf)
      endif()
    endif()
    set(ASM_EXT S)
    enable_language(ASM)
    set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -Wa,--noexecstack")

    # Clang's integerated assembler does not support debug symbols.
    if(NOT CMAKE_ASM_COMPILER_ID MATCHES "Clang")
      set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -Wa,-g")
    endif()

    # CMake does not add -isysroot and -arch flags to assembly.
    if (APPLE)
      if (CMAKE_OSX_SYSROOT)
        set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -isysroot \"${CMAKE_OSX_SYSROOT}\"")
      endif()
      foreach(arch ${CMAKE_OSX_ARCHITECTURES})
        set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -arch ${arch}")
      endforeach()
    endif()
  else()
    if (${ARCH} STREQUAL "x86_64")
      set(PERLASM_STYLE nasm)
    else()
      set(PERLASM_STYLE win32n)
      set(PERLASM_FLAGS "-DOPENSSL_IA32_SSE2")
    endif()
    set(CMAKE_ASM_NASM_FLAGS "-g cv8")

    # On Windows, we use the NASM output, specifically built with Yasm.
    set(ASM_EXT asm)
    enable_language(ASM_NASM)
  endif()
endif()

function(perlasm dest src)
  add_custom_command(
    OUTPUT ${dest}
    COMMAND ${PERL_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/${src} ${PERLASM_STYLE} ${PERLASM_FLAGS} ${ARGN} ${dest}
    DEPENDS
    ${src}
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/arm-xlate.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/ppc-xlate.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/x86_64-xlate.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/x86asm.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/x86gas.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/x86masm.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/x86nasm.pl
    WORKING_DIRECTORY .
  )
endfunction()

# Level 0.1 - depends on nothing outside this set.
add_subdirectory(stack)
add_subdirectory(lhash)
add_subdirectory(err)
add_subdirectory(buf)
add_subdirectory(base64)
add_subdirectory(bytestring)
add_subdirectory(pool)

# Level 0.2 - depends on nothing but itself
add_subdirectory(rc4)
add_subdirectory(conf)
add_subdirectory(chacha)
add_subdirectory(poly1305)
add_subdirectory(curve25519)

# Level 1, depends only on 0.*
add_subdirectory(digest_extra)
add_subdirectory(cipher_extra)
add_subdirectory(rand_extra)
add_subdirectory(bio)
add_subdirectory(bn_extra)
add_subdirectory(obj)
add_subdirectory(asn1)

# Level 2
add_subdirectory(engine)
add_subdirectory(dh)
add_subdirectory(dsa)
add_subdirectory(rsa_extra)
add_subdirectory(ec_extra)
add_subdirectory(ecdh)
add_subdirectory(ecdsa_extra)

# Level 3
add_subdirectory(cmac)
add_subdirectory(evp)
add_subdirectory(hkdf)
add_subdirectory(pem)
add_subdirectory(x509)
add_subdirectory(x509v3)

# Level 4
add_subdirectory(pkcs7)
add_subdirectory(pkcs8)

add_subdirectory(fipsmodule)

add_library(
  crypto_base

  OBJECT

  cpu-aarch64-linux.c
  cpu-arm.c
  cpu-arm-linux.c
  cpu-intel.c
  cpu-ppc64le.c
  crypto.c
  ex_data.c
  mem.c
  refcount_c11.c
  refcount_lock.c
  thread.c
  thread_none.c
  thread_pthread.c
  thread_win.c
)

if(FIPS_DELOCATE)
  SET_SOURCE_FILES_PROPERTIES(fipsmodule/bcm.o PROPERTIES EXTERNAL_OBJECT true)
  SET_SOURCE_FILES_PROPERTIES(fipsmodule/bcm.o PROPERTIES GENERATED true)

  set(
    CRYPTO_FIPS_OBJECTS

    fipsmodule/bcm.o
  )
endif()

add_library(
  crypto

  $<TARGET_OBJECTS:crypto_base>
  $<TARGET_OBJECTS:stack>
  $<TARGET_OBJECTS:lhash>
  $<TARGET_OBJECTS:err>
  $<TARGET_OBJECTS:base64>
  $<TARGET_OBJECTS:bytestring>
  $<TARGET_OBJECTS:pool>
  $<TARGET_OBJECTS:fipsmodule>
  $<TARGET_OBJECTS:digest_extra>
  $<TARGET_OBJECTS:cipher_extra>
  $<TARGET_OBJECTS:rc4>
  $<TARGET_OBJECTS:conf>
  $<TARGET_OBJECTS:chacha>
  $<TARGET_OBJECTS:poly1305>
  $<TARGET_OBJECTS:curve25519>
  $<TARGET_OBJECTS:fiat>
  $<TARGET_OBJECTS:buf>
  $<TARGET_OBJECTS:bn_extra>
  $<TARGET_OBJECTS:bio>
  $<TARGET_OBJECTS:rand_extra>
  $<TARGET_OBJECTS:obj>
  $<TARGET_OBJECTS:asn1>
  $<TARGET_OBJECTS:engine>
  $<TARGET_OBJECTS:dh>
  $<TARGET_OBJECTS:dsa>
  $<TARGET_OBJECTS:rsa_extra>
  $<TARGET_OBJECTS:ec_extra>
  $<TARGET_OBJECTS:ecdh>
  $<TARGET_OBJECTS:ecdsa_extra>
  $<TARGET_OBJECTS:cmac>
  $<TARGET_OBJECTS:evp>
  $<TARGET_OBJECTS:hkdf>
  $<TARGET_OBJECTS:pem>
  $<TARGET_OBJECTS:x509>
  $<TARGET_OBJECTS:x509v3>
  $<TARGET_OBJECTS:pkcs7>
  $<TARGET_OBJECTS:pkcs8_lib>

  ${CRYPTO_FIPS_OBJECTS}
)

if(FIPS_DELOCATE)
  add_dependencies(crypto bcm_o_target)
endif()

SET_TARGET_PROPERTIES(crypto PROPERTIES LINKER_LANGUAGE C)

if(NOT MSVC AND NOT ANDROID)
  target_link_libraries(crypto pthread)
endif()
