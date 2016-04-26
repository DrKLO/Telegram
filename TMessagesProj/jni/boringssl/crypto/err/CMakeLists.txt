include_directories(. .. ../../include)

add_custom_command(
  OUTPUT err_data.c
  COMMAND ${GO_EXECUTABLE} run err_data_generate.go > ${CMAKE_CURRENT_BINARY_DIR}/err_data.c
  DEPENDS
  err_data_generate.go
  asn1.errordata
  bio.errordata
  bn.errordata
  cipher.errordata
  conf.errordata
  dh.errordata
  digest.errordata
  dsa.errordata
  ecdh.errordata
  ecdsa.errordata
  ec.errordata
  engine.errordata
  evp.errordata
  hkdf.errordata
  obj.errordata
  pem.errordata
  pkcs8.errordata
  rsa.errordata
  ssl.errordata
  x509.errordata
  x509v3.errordata
  WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
)

add_library(
  err

  OBJECT

  err.c
  err_data.c
)

