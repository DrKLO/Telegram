include_directories(../../include)

add_library(
  bio

  OBJECT

  bio.c
  bio_mem.c
  connect.c
  fd.c
  file.c
  hexdump.c
  pair.c
  printf.c
  socket.c
  socket_helper.c
)
