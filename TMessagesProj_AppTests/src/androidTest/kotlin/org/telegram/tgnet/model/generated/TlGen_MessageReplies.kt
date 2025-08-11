package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessageReplies : TlGen_Object {
  public data class TL_messageReplies(
    public val replies: Int,
    public val replies_pts: Int,
    public val recent_repliers: List<TlGen_Peer>?,
    public val max_id: Int?,
    public val read_max_id: Int?,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_MessageReplies() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        if (recent_repliers != null) result = result or 2U
        if (max_id != null) result = result or 4U
        if (read_max_id != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(replies)
      stream.writeInt32(replies_pts)
      recent_repliers?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_0?.channel_id?.let { stream.writeInt64(it) }
      max_id?.let { stream.writeInt32(it) }
      read_max_id?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_0(
      public val comments: Boolean,
      public val channel_id: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x83D60FC2U
    }
  }

  public data class TL_messageReplies_layer132(
    public val replies: Int,
    public val replies_pts: Int,
    public val recent_repliers: List<TlGen_Peer>?,
    public val max_id: Int?,
    public val read_max_id: Int?,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        if (recent_repliers != null) result = result or 2U
        if (max_id != null) result = result or 4U
        if (read_max_id != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(replies)
      stream.writeInt32(replies_pts)
      recent_repliers?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_0?.channel_id?.let { stream.writeInt32(it) }
      max_id?.let { stream.writeInt32(it) }
      read_max_id?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_0(
      public val comments: Boolean,
      public val channel_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x4128FAACU
    }
  }
}
