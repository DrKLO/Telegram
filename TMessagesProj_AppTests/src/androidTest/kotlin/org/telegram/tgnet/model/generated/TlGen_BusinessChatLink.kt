package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BusinessChatLink : TlGen_Object {
  public data class TL_businessChatLink(
    public val link: String,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val title: String?,
    public val views: Int,
  ) : TlGen_BusinessChatLink() {
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
      stream.writeString(link)
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      title?.let { stream.writeString(it) }
      stream.writeInt32(views)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB4AE666FU
    }
  }
}
