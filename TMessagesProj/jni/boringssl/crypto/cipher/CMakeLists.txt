include_directories(. .. ../../include)

add_library(
  cipher

  OBJECT

  cipher.c
  derive_key.c
  aead.c

  e_null.c
  e_rc2.c
  e_rc4.c
  e_des.c
  e_aes.c
  e_chacha20poly1305.c

  tls_cbc.c
  e_tls.c
  e_ssl3.c
)
