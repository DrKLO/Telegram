include_directories(. .. ../../include)

if (${ARCH} STREQUAL "x86_64")
  set(
    RAND_ARCH_SOURCES

    rdrand-x86_64.${ASM_EXT}
  )
endif()

add_library(
  rand

  OBJECT

  rand.c
  urandom.c
  windows.c
  hwrand.c

  ${RAND_ARCH_SOURCES}
)

perlasm(rdrand-x86_64.${ASM_EXT} asm/rdrand-x86_64.pl)
