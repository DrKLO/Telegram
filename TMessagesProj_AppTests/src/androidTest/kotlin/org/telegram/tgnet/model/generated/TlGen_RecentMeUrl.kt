package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_RecentMeUrl : TlGen_Object {
  public data class TL_recentMeUrlUnknown(
    public val url: String,
  ) : TlGen_RecentMeUrl() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0x46E1D13DU
    }
  }

  public data class TL_recentMeUrlChatInvite(
    public val url: String,
    public val chat_invite: TlGen_ChatInvite,
  ) : TlGen_RecentMeUrl() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      chat_invite.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEB49081DU
    }
  }

  public data class TL_recentMeUrlStickerSet(
    public val url: String,
    public val `set`: TlGen_StickerSetCovered,
  ) : TlGen_RecentMeUrl() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      set.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBC0A57DCU
    }
  }

  public data class TL_recentMeUrlUser(
    public val url: String,
    public val user_id: Long,
  ) : TlGen_RecentMeUrl() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB92C09E2U
    }
  }

  public data class TL_recentMeUrlChat(
    public val url: String,
    public val chat_id: Long,
  ) : TlGen_RecentMeUrl() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeInt64(chat_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB2DA71D2U
    }
  }
}
