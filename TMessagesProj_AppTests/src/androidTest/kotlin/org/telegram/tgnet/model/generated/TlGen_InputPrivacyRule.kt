package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputPrivacyRule : TlGen_Object {
  public data object TL_inputPrivacyValueAllowContacts : TlGen_InputPrivacyRule() {
    public const val MAGIC: UInt = 0x0D09E07BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyValueAllowAll : TlGen_InputPrivacyRule() {
    public const val MAGIC: UInt = 0x184B35CEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputPrivacyValueAllowUsers(
    public val users: List<TlGen_InputUser>,
  ) : TlGen_InputPrivacyRule() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x131CC67FU
    }
  }

  public data object TL_inputPrivacyValueDisallowContacts : TlGen_InputPrivacyRule() {
    public const val MAGIC: UInt = 0x0BA52007U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyValueDisallowAll : TlGen_InputPrivacyRule() {
    public const val MAGIC: UInt = 0xD66B66C9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputPrivacyValueDisallowUsers(
    public val users: List<TlGen_InputUser>,
  ) : TlGen_InputPrivacyRule() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x90110467U
    }
  }

  public data class TL_inputPrivacyValueAllowChatParticipants(
    public val chats: List<Long>,
  ) : TlGen_InputPrivacyRule() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeLong(stream, chats)
    }

    public companion object {
      public const val MAGIC: UInt = 0x840649CFU
    }
  }

  public data class TL_inputPrivacyValueDisallowChatParticipants(
    public val chats: List<Long>,
  ) : TlGen_InputPrivacyRule() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeLong(stream, chats)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE94F0F86U
    }
  }

  public data object TL_inputPrivacyValueAllowCloseFriends : TlGen_InputPrivacyRule() {
    public const val MAGIC: UInt = 0x2F453E49U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyValueAllowPremium : TlGen_InputPrivacyRule() {
    public const val MAGIC: UInt = 0x77CDC9F1U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyValueAllowBots : TlGen_InputPrivacyRule() {
    public const val MAGIC: UInt = 0x5A4FCCE5U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyValueDisallowBots : TlGen_InputPrivacyRule() {
    public const val MAGIC: UInt = 0xC4E57915U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
