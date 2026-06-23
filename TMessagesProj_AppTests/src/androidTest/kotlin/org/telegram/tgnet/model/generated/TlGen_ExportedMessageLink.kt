package org.Tajgram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_ExportedMessageLink : TlGen_Object {
  public data class TL_exportedMessageLink(
    public val link: String,
    public val html: String,
  ) : TlGen_ExportedMessageLink() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(link)
      stream.writeString(html)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5DAB1AF4U
    }
  }
}
