package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BaseTheme : TlGen_Object {
  public data object TL_baseThemeClassic : TlGen_BaseTheme() {
    public const val MAGIC: UInt = 0xC3A12462U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_baseThemeDay : TlGen_BaseTheme() {
    public const val MAGIC: UInt = 0xFBD81688U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_baseThemeNight : TlGen_BaseTheme() {
    public const val MAGIC: UInt = 0xB7B31EA8U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_baseThemeTinted : TlGen_BaseTheme() {
    public const val MAGIC: UInt = 0x6D5F77EEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_baseThemeArctic : TlGen_BaseTheme() {
    public const val MAGIC: UInt = 0x5B11125AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
