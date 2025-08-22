package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PrivacyRule : TlGen_Object {
  public data object TL_privacyValueAllowContacts : TlGen_PrivacyRule() {
    public const val MAGIC: UInt = 0xFFFE1BACU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyValueAllowAll : TlGen_PrivacyRule() {
    public const val MAGIC: UInt = 0x65427B82U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyValueDisallowContacts : TlGen_PrivacyRule() {
    public const val MAGIC: UInt = 0xF888FA1AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyValueDisallowAll : TlGen_PrivacyRule() {
    public const val MAGIC: UInt = 0x8B73E763U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_privacyValueAllowUsers(
    public val users: List<Long>,
  ) : TlGen_PrivacyRule() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeLong(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB8905FB2U
    }
  }

  public data class TL_privacyValueDisallowUsers(
    public val users: List<Long>,
  ) : TlGen_PrivacyRule() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeLong(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE4621141U
    }
  }

  public data class TL_privacyValueAllowChatParticipants(
    public val chats: List<Long>,
  ) : TlGen_PrivacyRule() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeLong(stream, chats)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6B134E8EU
    }
  }

  public data class TL_privacyValueDisallowChatParticipants(
    public val chats: List<Long>,
  ) : TlGen_PrivacyRule() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeLong(stream, chats)
    }

    public companion object {
      public const val MAGIC: UInt = 0x41C87565U
    }
  }

  public data object TL_privacyValueAllowCloseFriends : TlGen_PrivacyRule() {
    public const val MAGIC: UInt = 0xF7E8D89BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyValueAllowPremium : TlGen_PrivacyRule() {
    public const val MAGIC: UInt = 0xECE9814BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyValueAllowBots : TlGen_PrivacyRule() {
    public const val MAGIC: UInt = 0x21461B5DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyValueDisallowBots : TlGen_PrivacyRule() {
    public const val MAGIC: UInt = 0xF6A5F82FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
