package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputWebDocument : TlGen_Object {
  public data class TL_inputWebDocument(
    public val url: String,
    public val size: Int,
    public val mime_type: String,
    public val attributes: List<TlGen_DocumentAttribute>,
  ) : TlGen_InputWebDocument() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeInt32(size)
      stream.writeString(mime_type)
      TlGen_Vector.serialize(stream, attributes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9BED434DU
    }
  }
}
