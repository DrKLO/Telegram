package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_updates_ChannelDifference : TlGen_Object {
  public data class TL_updates_channelDifferenceEmpty(
    public val `final`: Boolean,
    public val pts: Int,
    public val timeout: Int?,
  ) : TlGen_updates_ChannelDifference() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (final) result = result or 1U
        if (timeout != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(pts)
      timeout?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x3E11AFFBU
    }
  }

  public data class TL_updates_channelDifference(
    public val `final`: Boolean,
    public val pts: Int,
    public val timeout: Int?,
    public val new_messages: List<TlGen_Message>,
    public val other_updates: List<TlGen_Update>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_updates_ChannelDifference() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (final) result = result or 1U
        if (timeout != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(pts)
      timeout?.let { stream.writeInt32(it) }
      TlGen_Vector.serialize(stream, new_messages)
      TlGen_Vector.serialize(stream, other_updates)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2064674EU
    }
  }

  public data class TL_updates_channelDifferenceTooLong(
    public val `final`: Boolean,
    public val timeout: Int?,
    public val dialog: TlGen_Dialog,
    public val messages: List<TlGen_Message>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_updates_ChannelDifference() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (final) result = result or 1U
        if (timeout != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      timeout?.let { stream.writeInt32(it) }
      dialog.serializeToStream(stream)
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA4BCC6FEU
    }
  }
}
