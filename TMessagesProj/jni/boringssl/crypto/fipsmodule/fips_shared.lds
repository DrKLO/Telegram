SECTIONS
{
  .text : {
    BORINGSSL_bcm_text_start = .;
    *(.text)
    BORINGSSL_bcm_text_end = .;
  }
  .rodata : {
    BORINGSSL_bcm_rodata_start = .;
    *(.rodata)
    BORINGSSL_bcm_rodata_end = .;
  }

  /DISCARD/ : {
    *(.rela.dyn)
    *(.data)
    *(.rel.ro)
  }
}
