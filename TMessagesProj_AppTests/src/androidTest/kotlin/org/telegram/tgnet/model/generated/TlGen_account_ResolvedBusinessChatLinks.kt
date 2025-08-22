package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_ResolvedBusinessChatLinks : TlGen_Object {
  public data class TL_account_resolvedBusinessChatLinks(
    public val peer: TlGen_Peer,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_account_ResolvedBusinessChatLinks() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (entities != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9A23AF21U
    }
  }
}
