include_directories(../../include)

if (${ARCH} STREQUAL "x86_64")
  set(
    BCM_ASM_SOURCES

    aesni-gcm-x86_64.${ASM_EXT}
    aesni-x86_64.${ASM_EXT}
    aes-x86_64.${ASM_EXT}
    bsaes-x86_64.${ASM_EXT}
    ghash-x86_64.${ASM_EXT}
    md5-x86_64.${ASM_EXT}
    p256-x86_64-asm.${ASM_EXT}
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

if (${ARCH} STREQUAL "x86")
  set(
    BCM_ASM_SOURCES

    aes-586.${ASM_EXT}
    aesni-x86.${ASM_EXT}
    bn-586.${ASM_EXT}
    co-586.${ASM_EXT}
    ghash-x86.${ASM_EXT}
    md5-586.${ASM_EXT}
    sha1-586.${ASM_EXT}
    sha256-586.${ASM_EXT}
    sha512-586.${ASM_EXT}
    vpaes-x86.${ASM_EXT}
    x86-mont.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "arm")
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
  )
endif()

if (${ARCH} STREQUAL "aarch64")
  set(
    BCM_ASM_SOURCES

    aesv8-armx.${ASM_EXT}
    armv8-mont.${ASM_EXT}
    ghashv8-armx.${ASM_EXT}
    sha1-armv8.${ASM_EXT}
    sha256-armv8.${ASM_EXT}
    sha512-armv8.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "ppc64le")
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
perlasm(bsaes-x86_64.${ASM_EXT} aes/asm/bsaes-x86_64.pl)
perlasm(co-586.${ASM_EXT} bn/asm/co-586.pl)
perlasm(ghash-armv4.${ASM_EXT} modes/asm/ghash-armv4.pl)
perlasm(ghashp8-ppc.${ASM_EXT} modes/asm/ghashp8-ppc.pl)
perlasm(ghashv8-armx.${ASM_EXT} modes/asm/ghashv8-armx.pl)
perlasm(ghash-x86_64.${ASM_EXT} modes/asm/ghash-x86_64.pl)
perlasm(ghash-x86.${ASM_EXT} modes/asm/ghash-x86.pl)
perlasm(md5-586.${ASM_EXT} md5/asm/md5-586.pl)
perlasm(md5-x86_64.${ASM_EXT} md5/asm/md5-x86_64.pl)
perlasm(p256-x86_64-asm.${ASM_EXT} ec/asm/p256-x86_64-asm.pl)
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
perlasm(vpaes-x86_64.${ASM_EXT} aes/asm/vpaes-x86_64.pl)
perlasm(vpaes-x86.${ASM_EXT} aes/asm/vpaes-x86.pl)
perlasm(x86_64-mont5.${ASM_EXT} bn/asm/x86_64-mont5.pl)
perlasm(x86_64-mont.${ASM_EXT} bn/asm/x86_64-mont.pl)
perlasm(x86-mont.${ASM_EXT} bn/asm/x86-mont.pl)

if(FIPS_DELOCATE)
  add_library(
    bcm_c_generated_asm

    STATIC

    bcm.c
  )

  set_target_properties(bcm_c_generated_asm PROPERTIES COMPILE_OPTIONS "-S")
  set_target_properties(bcm_c_generated_asm PROPERTIES POSITION_INDEPENDENT_CODE ON)

  function(prepend_path values prefix output)
    set(result)
    foreach(value ${values})
      list(APPEND result "${prefix}/${value}")
    endforeach(value)
    set(${output} ${result} PARENT_SCOPE)
  endfunction()

  prepend_path("${BCM_ASM_SOURCES}" "${CMAKE_CURRENT_BINARY_DIR}" DELOCATE_ASM_ARGS)

  add_custom_command(
    OUTPUT bcm-delocated.S
    COMMAND ${GO_EXECUTABLE} run util/fipstools/delocate.go util/fipstools/delocate.peg.go util/fipstools/ar.go util/fipstools/const.go -a $<TARGET_FILE:bcm_c_generated_asm> -o ${CMAKE_CURRENT_BINARY_DIR}/bcm-delocated.S ${DELOCATE_ASM_ARGS}
    DEPENDS bcm_c_generated_asm ${BCM_ASM_SOURCES} ${CMAKE_SOURCE_DIR}/util/fipstools/delocate.go ${CMAKE_SOURCE_DIR}/util/fipstools/delocate.peg.go ${CMAKE_SOURCE_DIR}/util/fipstools/ar.go ${CMAKE_SOURCE_DIR}/util/fipstools/const.go
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
  )

  add_library(
    bcm_hashunset

    STATIC

    bcm-delocated.S
  )

  set_target_properties(bcm_hashunset PROPERTIES POSITION_INDEPENDENT_CODE ON)
  set_target_properties(bcm_hashunset PROPERTIES LINKER_LANGUAGE C)

  add_custom_command(
    OUTPUT bcm.o
    COMMAND ${GO_EXECUTABLE} run util/fipstools/inject-hash.go util/fipstools/ar.go util/fipstools/const.go -o ${CMAKE_CURRENT_BINARY_DIR}/bcm.o -in-archive $<TARGET_FILE:bcm_hashunset>
    DEPENDS bcm_hashunset ${CMAKE_SOURCE_DIR}/util/fipstools/inject-hash.go ${CMAKE_SOURCE_DIR}/util/fipstools/ar.go ${CMAKE_SOURCE_DIR}/util/fipstools/const.go
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
  )

  # The outputs of add_custom_command cannot be referenced outside of the
  # CMakeLists.txt that defines it. Thus we have to wrap bcm.o in a custom target
  # so that crypto can depend on it.
  add_custom_target(bcm_o_target DEPENDS bcm.o)

  add_library(
    fipsmodule

    OBJECT

    is_fips.c
  )

  set_target_properties(fipsmodule PROPERTIES LINKER_LANGUAGE C)
else()
  add_library(
    fipsmodule

    OBJECT

    bcm.c
    is_fips.c

    aes/aes_ige.c

    ${BCM_ASM_SOURCES}
  )
endif()
