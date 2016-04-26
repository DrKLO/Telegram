include_directories(. .. ../../include)

if (${ARCH} STREQUAL "x86_64")
  set(
    AES_ARCH_SOURCES

    aes-x86_64.${ASM_EXT}
    aesni-x86_64.${ASM_EXT}
    bsaes-x86_64.${ASM_EXT}
    vpaes-x86_64.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "x86")
  set(
    AES_ARCH_SOURCES

    aes-586.${ASM_EXT}
    vpaes-x86.${ASM_EXT}
    aesni-x86.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "arm")
  set(
    AES_ARCH_SOURCES

    aes-armv4.${ASM_EXT}
    bsaes-armv7.${ASM_EXT}
    aesv8-armx.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "aarch64")
  set(
    AES_ARCH_SOURCES

    aesv8-armx.${ASM_EXT}
  )
endif()

add_library(
  aes

  OBJECT

  aes.c
  aes_ige.c
  mode_wrappers.c

  ${AES_ARCH_SOURCES}
)

perlasm(aes-x86_64.${ASM_EXT} asm/aes-x86_64.pl)
perlasm(aesni-x86_64.${ASM_EXT} asm/aesni-x86_64.pl)
perlasm(bsaes-x86_64.${ASM_EXT} asm/bsaes-x86_64.pl)
perlasm(vpaes-x86_64.${ASM_EXT} asm/vpaes-x86_64.pl)
perlasm(aes-586.${ASM_EXT} asm/aes-586.pl)
perlasm(vpaes-x86.${ASM_EXT} asm/vpaes-x86.pl)
perlasm(aesni-x86.${ASM_EXT} asm/aesni-x86.pl)
perlasm(aes-armv4.${ASM_EXT} asm/aes-armv4.pl)
perlasm(bsaes-armv7.${ASM_EXT} asm/bsaes-armv7.pl)
perlasm(aesv8-armx.${ASM_EXT} asm/aesv8-armx.pl)
