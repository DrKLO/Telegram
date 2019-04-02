include_directories(../../include)

if (${ARCH} STREQUAL "x86_64")
  set(
    CIPHER_ARCH_SOURCES

    aes128gcmsiv-x86_64.${ASM_EXT}
    chacha20_poly1305_x86_64.${ASM_EXT}
  )
endif()

add_library(
  cipher_extra

  OBJECT

  cipher_extra.c
  derive_key.c

  e_null.c
  e_rc2.c
  e_rc4.c
  e_aesgcmsiv.c
  e_aesctrhmac.c
  e_chacha20poly1305.c

  tls_cbc.c
  e_tls.c
  e_ssl3.c

  ${CIPHER_ARCH_SOURCES}
)

perlasm(aes128gcmsiv-x86_64.${ASM_EXT} asm/aes128gcmsiv-x86_64.pl)
perlasm(chacha20_poly1305_x86_64.${ASM_EXT} asm/chacha20_poly1305_x86_64.pl)
