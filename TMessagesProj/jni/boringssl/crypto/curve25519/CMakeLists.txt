include_directories(../../include)

if (${ARCH} STREQUAL "arm")
  set(
    CURVE25519_ARCH_SOURCES

    asm/x25519-asm-arm.S
  )
endif()

if (${ARCH} STREQUAL "x86_64")
  set(
    CURVE25519_ARCH_SOURCES

    asm/x25519-asm-x86_64.S
  )
endif()

add_library(
  curve25519

  OBJECT

  spake25519.c
  x25519-x86_64.c

  ${CURVE25519_ARCH_SOURCES}
)
