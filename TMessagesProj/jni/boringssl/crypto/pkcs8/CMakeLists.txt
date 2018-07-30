include_directories(../../include)

add_library(
  pkcs8_lib

  OBJECT

  pkcs8.c
  pkcs8_x509.c
  p5_pbev2.c
)
