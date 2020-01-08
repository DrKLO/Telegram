include_directories(../../include)

if(${ARCH} STREQUAL "x86_64")
  set(
    BCM_ASM_SOURCES

    aesni-gcm-x86_64.${ASM_EXT}
    aesni-x86_64.${ASM_EXT}
    aes-x86_64.${ASM_EXT}
    ghash-ssse3-x86_64.${ASM_EXT}
    ghash-x86_64.${ASM_EXT}
    md5-x86_64.${ASM_EXT}
    p256-x86_64-asm.${ASM_EXT}
    p256_beeu-x86_64-asm.${ASM_EXT}
    rdrand-x86_64.${ASM_EXT}
    rsaz-avx2.${ASM_EXT}
    sha1-x86_64.${ASM_EXT}
    sha256-x86_64.${ASM_EXT}
    sha512-x86_64.${ASM_EXT}
    vpaes-x86_64.${ASM_EXT}
    x86_64-mont5.${ASM_EXT}
    x86_64-mont.${ASM_EXT}
  )
endif()

if(${ARCH} STREQUAL "x86")
  set(
    BCM_ASM_SOURCES

    aes-586.${ASM_EXT}
    aesni-x86.${ASM_EXT}
    bn-586.${ASM_EXT}
    co-586.${ASM_EXT}
    ghash-ssse3-x86.${ASM_EXT}
    ghash-x86.${ASM_EXT}
    md5-586.${ASM_EXT}
    sha1-586.${ASM_EXT}
    sha256-586.${ASM_EXT}
    sha512-586.${ASM_EXT}
    vpaes-x86.${ASM_EXT}
    x86-mont.${ASM_EXT}
  )
endif()

if(${ARCH} STREQUAL "arm")
  set(
    BCM_ASM_SOURCES

    aes-armv4.${ASM_EXT}
    aesv8-armx.${ASM_EXT}
    armv4-mont.${ASM_EXT}
    bsaes-armv7.${ASM_EXT}
    ghash-armv4.${ASM_EXT}
    ghashv8-armx.${ASM_EXT}
    sha1-armv4-large.${ASM_EXT}
    sha256-armv4.${ASM_EXT}
    sha512-armv4.${ASM_EXT}
    vpaes-armv7.${ASM_EXT}
  )
endif()

if(${ARCH} STREQUAL "aarch64")
  set(
    BCM_ASM_SOURCES

    aesv8-armx.${ASM_EXT}
    armv8-mont.${ASM_EXT}
    ghash-neon-armv8.${ASM_EXT}
    ghashv8-armx.${ASM_EXT}
    sha1-armv8.${ASM_EXT}
    sha256-armv8.${ASM_EXT}
    sha512-armv8.${ASM_EXT}
    vpaes-armv8.${ASM_EXT}
  )
endif()

if(${ARCH} STREQUAL "ppc64le")
  set(
    BCM_ASM_SOURCES

    aesp8-ppc.${ASM_EXT}
    ghashp8-ppc.${ASM_EXT}
  )
endif()

perlasm(aes-586.${ASM_EXT} aes/asm/aes-586.pl)
perlasm(aes-armv4.${ASM_EXT} aes/asm/aes-armv4.pl)
perlasm(aesni-gcm-x86_64.${ASM_EXT} modes/asm/aesni-gcm-x86_64.pl)
perlasm(aesni-x86_64.${ASM_EXT} aes/asm/aesni-x86_64.pl)
perlasm(aesni-x86.${ASM_EXT} aes/asm/aesni-x86.pl)
perlasm(aesp8-ppc.${ASM_EXT} aes/asm/aesp8-ppc.pl)
perlasm(aesv8-armx.${ASM_EXT} aes/asm/aesv8-armx.pl)
perlasm(aes-x86_64.${ASM_EXT} aes/asm/aes-x86_64.pl)
perlasm(armv4-mont.${ASM_EXT} bn/asm/armv4-mont.pl)
perlasm(armv8-mont.${ASM_EXT} bn/asm/armv8-mont.pl)
perlasm(bn-586.${ASM_EXT} bn/asm/bn-586.pl)
perlasm(bsaes-armv7.${ASM_EXT} aes/asm/bsaes-armv7.pl)
perlasm(co-586.${ASM_EXT} bn/asm/co-586.pl)
perlasm(ghash-armv4.${ASM_EXT} modes/asm/ghash-armv4.pl)
perlasm(ghashp8-ppc.${ASM_EXT} modes/asm/ghashp8-ppc.pl)
perlasm(ghashv8-armx.${ASM_EXT} modes/asm/ghashv8-armx.pl)
perlasm(ghash-neon-armv8.${ASM_EXT} modes/asm/ghash-neon-armv8.pl)
perlasm(ghash-ssse3-x86_64.${ASM_EXT} modes/asm/ghash-ssse3-x86_64.pl)
perlasm(ghash-ssse3-x86.${ASM_EXT} modes/asm/ghash-ssse3-x86.pl)
perlasm(ghash-x86_64.${ASM_EXT} modes/asm/ghash-x86_64.pl)
perlasm(ghash-x86.${ASM_EXT} modes/asm/ghash-x86.pl)
perlasm(md5-586.${ASM_EXT} md5/asm/md5-586.pl)
perlasm(md5-x86_64.${ASM_EXT} md5/asm/md5-x86_64.pl)
perlasm(p256-x86_64-asm.${ASM_EXT} ec/asm/p256-x86_64-asm.pl)
perlasm(p256_beeu-x86_64-asm.${ASM_EXT} ec/asm/p256_beeu-x86_64-asm.pl)
perlasm(rdrand-x86_64.${ASM_EXT} rand/asm/rdrand-x86_64.pl)
perlasm(rsaz-avx2.${ASM_EXT} bn/asm/rsaz-avx2.pl)
perlasm(sha1-586.${ASM_EXT} sha/asm/sha1-586.pl)
perlasm(sha1-armv4-large.${ASM_EXT} sha/asm/sha1-armv4-large.pl)
perlasm(sha1-armv8.${ASM_EXT} sha/asm/sha1-armv8.pl)
perlasm(sha1-x86_64.${ASM_EXT} sha/asm/sha1-x86_64.pl)
perlasm(sha256-586.${ASM_EXT} sha/asm/sha256-586.pl)
perlasm(sha256-armv4.${ASM_EXT} sha/asm/sha256-armv4.pl)
perlasm(sha256-armv8.${ASM_EXT} sha/asm/sha512-armv8.pl)
perlasm(sha256-x86_64.${ASM_EXT} sha/asm/sha512-x86_64.pl)
perlasm(sha512-586.${ASM_EXT} sha/asm/sha512-586.pl)
perlasm(sha512-armv4.${ASM_EXT} sha/asm/sha512-armv4.pl)
perlasm(sha512-armv8.${ASM_EXT} sha/asm/sha512-armv8.pl)
perlasm(sha512-x86_64.${ASM_EXT} sha/asm/sha512-x86_64.pl)
perlasm(vpaes-armv7.${ASM_EXT} aes/asm/vpaes-armv7.pl)
perlasm(vpaes-armv8.${ASM_EXT} aes/asm/vpaes-armv8.pl)
perlasm(vpaes-x86_64.${ASM_EXT} aes/asm/vpaes-x86_64.pl)
perlasm(vpaes-x86.${ASM_EXT} aes/asm/vpaes-x86.pl)
perlasm(x86_64-mont5.${ASM_EXT} bn/asm/x86_64-mont5.pl)
perlasm(x86_64-mont.${ASM_EXT} bn/asm/x86_64-mont.pl)
perlasm(x86-mont.${ASM_EXT} bn/asm/x86-mont.pl)

if(FIPS_DELOCATE)
  if(FIPS_SHARED)
    error("Can't set both delocate and shared mode for FIPS build")
  endif()

  if(OPENSSL_NO_ASM)
    # If OPENSSL_NO_ASM was defined then ASM will not have been enabled, but in
    # FIPS mode we have to have it because the module build requires going via
    # textual assembly.
    enable_language(ASM)
  endif()

  add_library(
    bcm_c_generated_asm

    STATIC

    bcm.c
  )

  add_dependencies(bcm_c_generated_asm global_target)

  set_target_properties(bcm_c_generated_asm PROPERTIES COMPILE_OPTIONS "-S")
  set_target_properties(bcm_c_generated_asm PROPERTIES POSITION_INDEPENDENT_CODE ON)

  go_executable(delocate boringssl.googlesource.com/boringssl/util/fipstools/delocate)
  add_custom_command(
    OUTPUT bcm-delocated.S
    COMMAND ./delocate -a $<TARGET_FILE:bcm_c_generated_asm> -o bcm-delocated.S ${BCM_ASM_SOURCES}
    DEPENDS bcm_c_generated_asm delocate ${BCM_ASM_SOURCES}
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
  )

  add_library(
    bcm_hashunset

    STATIC

    bcm-delocated.S
  )

  add_dependencies(bcm_hashunset global_target)

  set_target_properties(bcm_hashunset PROPERTIES POSITION_INDEPENDENT_CODE ON)
  set_target_properties(bcm_hashunset PROPERTIES LINKER_LANGUAGE C)

  go_executable(inject_hash
	        boringssl.googlesource.com/boringssl/util/fipstools/inject_hash)
  add_custom_command(
    OUTPUT bcm.o
    COMMAND ./inject_hash -o bcm.o -in-archive $<TARGET_FILE:bcm_hashunset>
    DEPENDS bcm_hashunset inject_hash
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
  )

  # The outputs of add_custom_command cannot be referenced outside of the
  # CMakeLists.txt that defines it. Thus we have to wrap bcm.o in a custom target
  # so that crypto can depend on it.
  add_custom_target(bcm_o_target DEPENDS bcm.o)

  add_library(
    fipsmodule

    OBJECT

    fips_shared_support.c
    is_fips.c
  )

  add_dependencies(fipsmodule global_target)

  set_target_properties(fipsmodule PROPERTIES LINKER_LANGUAGE C)
elseif(FIPS_SHARED)
  if(NOT BUILD_SHARED_LIBS)
    error("FIPS_SHARED set but not BUILD_SHARED_LIBS")
  endif()

  add_library(
    fipsmodule

    OBJECT

    fips_shared_support.c
    is_fips.c
  )

  add_dependencies(fipsmodule global_target)

  add_library(
    bcm_library

    STATIC

    bcm.c

    ${BCM_ASM_SOURCES}
  )

  add_dependencies(bcm_library global_target)

  add_custom_command(
    OUTPUT bcm.o
    COMMAND ld -r -T ${CMAKE_CURRENT_SOURCE_DIR}/fips_shared.lds -o bcm.o --whole-archive $<TARGET_FILE:bcm_library>
    DEPENDS bcm_library fips_shared.lds
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
  )

  add_custom_target(bcm_o_target DEPENDS bcm.o)
else()
  add_library(
    fipsmodule

    OBJECT

    bcm.c
    fips_shared_support.c
    is_fips.c

    aes/aes_ige.c

    ${BCM_ASM_SOURCES}
  )

  add_dependencies(fipsmodule global_target)
endif()
