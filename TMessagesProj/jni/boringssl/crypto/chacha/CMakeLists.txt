include_directories(. .. ../../include)

if (${ARCH} STREQUAL "arm")
  set(
    CHACHA_ARCH_SOURCES

    chacha_vec_arm.S
  )
endif()

add_library(
  chacha

  OBJECT

  chacha_generic.c
  chacha_vec.c

  ${CHACHA_ARCH_SOURCES}
)
