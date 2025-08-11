package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BusinessIntro : TlGen_Object {
  public data class TL_businessIntro(
    public val title: String,
    public val description: String,
    public val sticker: TlGen_Document?,
  ) : TlGen_BusinessIntro() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (sticker != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(title)
      stream.writeString(description)
      sticker?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5A0A066DU
    }
  }
}
