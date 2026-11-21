package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ExportedNoPaidMessagesToken : TlGen_Object {
  public data class TL_exportedNoPaidMessagesToken(
    public val token: String,
    public val url: String,
    public val expires: Int?,
  ) : TlGen_ExportedNoPaidMessagesToken() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (expires != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(token)
      stream.writeString(url)
      expires?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x695DD042U
    }
  }
}
