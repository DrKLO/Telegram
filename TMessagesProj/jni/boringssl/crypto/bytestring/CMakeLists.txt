include_directories(. .. ../../include)

add_library(
  bytestring

  OBJECT

  ber.c
  cbs.c
  cbb.c
)
