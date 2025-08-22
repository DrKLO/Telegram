package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_contacts_TopPeers : TlGen_Object {
  public data object TL_contacts_topPeersNotModified : TlGen_contacts_TopPeers() {
    public const val MAGIC: UInt = 0xDE266EF5U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_contacts_topPeers(
    public val categories: List<TlGen_TopPeerCategoryPeers>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_contacts_TopPeers() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, categories)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x70B772A8U
    }
  }

  public data object TL_contacts_topPeersDisabled : TlGen_contacts_TopPeers() {
    public const val MAGIC: UInt = 0xB52C939DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
