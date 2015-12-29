include_directories(. .. ../../include)

add_library(
  ec

  OBJECT

  ec.c
  ec_asn1.c
  ec_key.c
  ec_montgomery.c
  oct.c
  p256-64.c
  util-64.c
  simple.c
  wnaf.c
)
