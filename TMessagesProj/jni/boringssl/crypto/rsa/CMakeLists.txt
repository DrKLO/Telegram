include_directories(. .. ../../include)

add_library(
  rsa

  OBJECT

  rsa.c
  rsa_impl.c
  blinding.c
  padding.c
  rsa_asn1.c
)
