include_directories(. .. ../../include)

add_library(
  dsa

  OBJECT

  dsa.c
  dsa_impl.c
  dsa_asn1.c
)
