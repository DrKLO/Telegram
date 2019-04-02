include_directories(../../include)

add_library(
  pem

  OBJECT

  pem_all.c
  pem_info.c
  pem_lib.c
  pem_oth.c
  pem_pk8.c
  pem_pkey.c
  pem_x509.c
  pem_xaux.c
)
