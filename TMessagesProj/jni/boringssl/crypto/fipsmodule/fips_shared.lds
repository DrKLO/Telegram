SECTIONS
{
  .text : {
    BORINGSSL_bcm_text_start = .;
    *(.text)
    *(.text.unlikely.*)
    /* These sections shouldn't exist. But C++ `inline` symbols seem to be
     * placed in function sections on Android even with
     * -fno-function-sections. */
    *(text.*)
    BORINGSSL_bcm_text_end = .;
  }
  .rodata : {
    BORINGSSL_bcm_rodata_start = .;
    *(.rodata)
    *(.rodata.*)
    BORINGSSL_bcm_rodata_end = .;
  }

  /DISCARD/ : {
    /* These sections shouldn't exist. In order to catch any slip-ups, direct
     * the linker to discard them. */
    *(.rela.dyn)
    *(.data)
    *(.rel.ro)
    *(*.data.*)
  }
}
