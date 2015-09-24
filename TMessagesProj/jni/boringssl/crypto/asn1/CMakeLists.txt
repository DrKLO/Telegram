include_directories(. .. ../../include)

add_library(
  asn1

  OBJECT

  a_bitstr.c
  a_bool.c
  a_bytes.c
  a_d2i_fp.c
  a_dup.c
  a_enum.c
  a_gentm.c
  a_i2d_fp.c
  a_int.c
  a_mbstr.c
  a_object.c
  a_octet.c
  a_print.c
  a_strnid.c
  a_time.c
  a_type.c
  a_utctm.c
  a_utf8.c
  asn1_lib.c
  asn1_par.c
  asn_pack.c
  bio_asn1.c
  bio_ndef.c
  f_enum.c
  f_int.c
  f_string.c
  t_bitst.c
  t_pkey.c
  tasn_dec.c
  tasn_enc.c
  tasn_fre.c
  tasn_new.c
  tasn_prn.c
  tasn_typ.c
  tasn_utl.c
  x_bignum.c
  x_long.c
)
