include_directories(../../include)

add_library(
  bytestring

  OBJECT

  asn1_compat.c
  ber.c
  cbs.c
  cbb.c
)
