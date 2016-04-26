include_directories(. .. ../../include)

if (${ARCH} STREQUAL "x86_64")
  set(
    SHA_ARCH_SOURCES

    sha1-x86_64.${ASM_EXT}
    sha256-x86_64.${ASM_EXT}
    sha512-x86_64.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "x86")
  set(
    SHA_ARCH_SOURCES

    sha1-586.${ASM_EXT}
    sha256-586.${ASM_EXT}
    sha512-586.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "arm")
  set(
    SHA_ARCH_SOURCES

    sha1-armv4-large.${ASM_EXT}
    sha256-armv4.${ASM_EXT}
    sha512-armv4.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "aarch64")
  set(
    SHA_ARCH_SOURCES

    sha1-armv8.${ASM_EXT}
    sha256-armv8.${ASM_EXT}
    sha512-armv8.${ASM_EXT}
  )
endif()

add_library(
  sha

  OBJECT

  sha1.c
  sha256.c
  sha512.c

  ${SHA_ARCH_SOURCES}
)

perlasm(sha1-x86_64.${ASM_EXT} asm/sha1-x86_64.pl)
perlasm(sha256-x86_64.${ASM_EXT} asm/sha512-x86_64.pl sha256)
perlasm(sha512-x86_64.${ASM_EXT} asm/sha512-x86_64.pl sha512)
perlasm(sha1-586.${ASM_EXT} asm/sha1-586.pl)
perlasm(sha256-586.${ASM_EXT} asm/sha256-586.pl)
perlasm(sha512-586.${ASM_EXT} asm/sha512-586.pl)
perlasm(sha1-armv4-large.${ASM_EXT} asm/sha1-armv4-large.pl)
perlasm(sha256-armv4.${ASM_EXT} asm/sha256-armv4.pl)
perlasm(sha512-armv4.${ASM_EXT} asm/sha512-armv4.pl)
perlasm(sha1-armv8.${ASM_EXT} asm/sha1-armv8.pl)
perlasm(sha256-armv8.${ASM_EXT} asm/sha512-armv8.pl sha256)
perlasm(sha512-armv8.${ASM_EXT} asm/sha512-armv8.pl sha512)
