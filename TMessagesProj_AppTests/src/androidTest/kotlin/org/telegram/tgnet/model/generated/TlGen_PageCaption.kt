package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PageCaption : TlGen_Object {
  public data class TL_pageCaption(
    public val text: TlGen_RichText,
    public val credit: TlGen_RichText,
  ) : TlGen_PageCaption() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      credit.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6F747657U
    }
  }
}
