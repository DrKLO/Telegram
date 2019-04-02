include_directories(../../include)

add_library(
  evp

  OBJECT

  digestsign.c
  evp.c
  evp_asn1.c
  evp_ctx.c
  p_dsa_asn1.c
  p_ec.c
  p_ec_asn1.c
  p_ed25519.c
  p_ed25519_asn1.c
  p_rsa.c
  p_rsa_asn1.c
  pbkdf.c
  print.c
  scrypt.c
  sign.c
)
