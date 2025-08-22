package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChannelParticipantsFilter : TlGen_Object {
  public data object TL_channelParticipantsRecent : TlGen_ChannelParticipantsFilter() {
    public const val MAGIC: UInt = 0xDE3F3C79U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_channelParticipantsAdmins : TlGen_ChannelParticipantsFilter() {
    public const val MAGIC: UInt = 0xB4608969U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_channelParticipantsBots : TlGen_ChannelParticipantsFilter() {
    public const val MAGIC: UInt = 0xB0D1865BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_channelParticipantsKicked(
    public val q: String,
  ) : TlGen_ChannelParticipantsFilter() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(q)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA3B54985U
    }
  }

  public data class TL_channelParticipantsBanned(
    public val q: String,
  ) : TlGen_ChannelParticipantsFilter() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(q)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1427A5E1U
    }
  }

  public data class TL_channelParticipantsSearch(
    public val q: String,
  ) : TlGen_ChannelParticipantsFilter() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(q)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0656AC4BU
    }
  }

  public data class TL_channelParticipantsContacts(
    public val q: String,
  ) : TlGen_ChannelParticipantsFilter() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(q)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBB6AE88DU
    }
  }

  public data class TL_channelParticipantsMentions(
    public val q: String?,
    public val top_msg_id: Int?,
  ) : TlGen_ChannelParticipantsFilter() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (q != null) result = result or 1U
        if (top_msg_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      q?.let { stream.writeString(it) }
      top_msg_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xE04B5CEBU
    }
  }
}
