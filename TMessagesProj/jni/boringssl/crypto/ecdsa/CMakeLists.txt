include_directories(. .. ../../include)

add_library(
  ecdsa

  OBJECT

  ecdsa.c
  ecdsa_asn1.c
)
