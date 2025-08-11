package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_stickers_SuggestedShortName : TlGen_Object {
  public data class TL_stickers_suggestedShortName(
    public val short_name: String,
  ) : TlGen_stickers_SuggestedShortName() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(short_name)
    }

    public companion object {
      public const val MAGIC: UInt = 0x85FEA03FU
    }
  }
}
