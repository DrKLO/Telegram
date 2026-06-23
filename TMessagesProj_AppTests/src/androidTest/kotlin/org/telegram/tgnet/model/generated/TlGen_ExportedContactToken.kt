package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_ExportedContactToken : TlGen_Object {
  public data class TL_exportedContactToken(
    public val url: String,
    public val expires: Int,
  ) : TlGen_ExportedContactToken() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeInt32(expires)
    }

    public companion object {
      public const val MAGIC: UInt = 0x41BF109BU
    }
  }
}
