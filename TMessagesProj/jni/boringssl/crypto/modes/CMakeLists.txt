include_directories(. .. ../../include)

if (${ARCH} STREQUAL "x86_64")
  set(
    MODES_ARCH_SOURCES

    aesni-gcm-x86_64.${ASM_EXT}
    ghash-x86_64.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "x86")
  set(
    MODES_ARCH_SOURCES

    ghash-x86.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "arm")
  set(
    MODES_ARCH_SOURCES

    ghash-armv4.${ASM_EXT}
    ghashv8-armx.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "aarch64")
  set(
    MODES_ARCH_SOURCES

    ghashv8-armx.${ASM_EXT}
  )
endif()

add_library(
  modes

  OBJECT

  cbc.c
  ctr.c
  ofb.c
  cfb.c
  gcm.c

  ${MODES_ARCH_SOURCES}
)

perlasm(aesni-gcm-x86_64.${ASM_EXT} asm/aesni-gcm-x86_64.pl)
perlasm(ghash-x86_64.${ASM_EXT} asm/ghash-x86_64.pl)
perlasm(ghash-x86.${ASM_EXT} asm/ghash-x86.pl)
perlasm(ghash-armv4.${ASM_EXT} asm/ghash-armv4.pl)
perlasm(ghashv8-armx.${ASM_EXT} asm/ghashv8-armx.pl)
