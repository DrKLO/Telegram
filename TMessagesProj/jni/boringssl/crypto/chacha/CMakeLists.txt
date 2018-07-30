include_directories(../../include)

if (${ARCH} STREQUAL "arm")
  set(
    CHACHA_ARCH_SOURCES

    chacha-armv4.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "aarch64")
  set(
    CHACHA_ARCH_SOURCES

    chacha-armv8.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "x86")
  set(
    CHACHA_ARCH_SOURCES

    chacha-x86.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "x86_64")
  set(
    CHACHA_ARCH_SOURCES

    chacha-x86_64.${ASM_EXT}
  )
endif()

add_library(
  chacha

  OBJECT

  chacha.c

  ${CHACHA_ARCH_SOURCES}
)

perlasm(chacha-armv4.${ASM_EXT} asm/chacha-armv4.pl)
perlasm(chacha-armv8.${ASM_EXT} asm/chacha-armv8.pl)
perlasm(chacha-x86.${ASM_EXT} asm/chacha-x86.pl)
perlasm(chacha-x86_64.${ASM_EXT} asm/chacha-x86_64.pl)
