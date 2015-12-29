include_directories(. .. ../../include)

add_library(
  evp

  OBJECT

  algorithm.c
  digestsign.c
  evp.c
  evp_asn1.c
  evp_ctx.c
  p_dsa_asn1.c
  p_ec.c
  p_ec_asn1.c
  p_rsa.c
  p_rsa_asn1.c
  pbkdf.c
  sign.c
)
