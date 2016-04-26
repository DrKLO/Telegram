include_directories(. .. ../../include)

if (${ARCH} STREQUAL "x86_64")
  set(
    BN_ARCH_SOURCES

    x86_64-mont.${ASM_EXT}
    x86_64-mont5.${ASM_EXT}
    rsaz-x86_64.${ASM_EXT}
    rsaz-avx2.${ASM_EXT}

    rsaz_exp.c
  )
endif()

if (${ARCH} STREQUAL "x86")
  set(
    BN_ARCH_SOURCES

    bn-586.${ASM_EXT}
    co-586.${ASM_EXT}
    x86-mont.${ASM_EXT}
  )
endif()

if (${ARCH} STREQUAL "arm")
  set(
    BN_ARCH_SOURCES

    armv4-mont.${ASM_EXT}
  )
endif()

add_library(
  bn

  OBJECT

  add.c
  asm/x86_64-gcc.c
  bn.c
  bn_asn1.c
  cmp.c
  convert.c
  ctx.c
  div.c
  exponentiation.c
  generic.c
  gcd.c
  kronecker.c
  montgomery.c
  mul.c
  prime.c
  random.c
  shift.c
  sqrt.c

  ${BN_ARCH_SOURCES}
)

perlasm(x86_64-mont.${ASM_EXT} asm/x86_64-mont.pl)
perlasm(x86_64-mont5.${ASM_EXT} asm/x86_64-mont5.pl)
perlasm(rsaz-x86_64.${ASM_EXT} asm/rsaz-x86_64.pl)
perlasm(rsaz-avx2.${ASM_EXT} asm/rsaz-avx2.pl)
perlasm(bn-586.${ASM_EXT} asm/bn-586.pl)
perlasm(co-586.${ASM_EXT} asm/co-586.pl)
perlasm(x86-mont.${ASM_EXT} asm/x86-mont.pl)
perlasm(armv4-mont.${ASM_EXT} asm/armv4-mont.pl)
