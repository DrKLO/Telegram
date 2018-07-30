include_directories(../../include)

add_library(
  dh

  OBJECT

  dh.c
  params.c
  check.c
  dh_asn1.c
)
