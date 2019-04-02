include_directories(../../include)

add_library(
  rand_extra

  OBJECT

  deterministic.c
  forkunsafe.c
  fuchsia.c
  rand_extra.c
  windows.c
)
