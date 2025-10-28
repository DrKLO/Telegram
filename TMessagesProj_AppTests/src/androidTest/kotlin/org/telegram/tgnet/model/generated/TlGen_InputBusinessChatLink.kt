package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputBusinessChatLink : TlGen_Object {
  public data class TL_inputBusinessChatLink(
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val title: String?,
  ) : TlGen_InputBusinessChatLink() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (entities != null) result = result or 1U
        if (title != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      title?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x11679FA7U
    }
  }
}
