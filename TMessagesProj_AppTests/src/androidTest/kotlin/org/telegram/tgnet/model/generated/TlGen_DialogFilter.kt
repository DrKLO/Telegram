package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_DialogFilter : TlGen_Object {
  public data object TL_dialogFilterDefault : TlGen_DialogFilter() {
    public const val MAGIC: UInt = 0x363293AEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_dialogFilter(
    public val contacts: Boolean,
    public val non_contacts: Boolean,
    public val groups: Boolean,
    public val broadcasts: Boolean,
    public val bots: Boolean,
    public val exclude_muted: Boolean,
    public val exclude_read: Boolean,
    public val exclude_archived: Boolean,
    public val title_noanimate: Boolean,
    public val id: Int,
    public val title: TlGen_TextWithEntities,
    public val emoticon: String?,
    public val color: Int?,
    public val pinned_peers: List<TlGen_InputPeer>,
    public val include_peers: List<TlGen_InputPeer>,
    public val exclude_peers: List<TlGen_InputPeer>,
  ) : TlGen_DialogFilter() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (contacts) result = result or 1U
        if (non_contacts) result = result or 2U
        if (groups) result = result or 4U
        if (broadcasts) result = result or 8U
        if (bots) result = result or 16U
        if (exclude_muted) result = result or 2048U
        if (exclude_read) result = result or 4096U
        if (exclude_archived) result = result or 8192U
        if (emoticon != null) result = result or 33554432U
        if (color != null) result = result or 134217728U
        if (title_noanimate) result = result or 268435456U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      title.serializeToStream(stream)
      emoticon?.let { stream.writeString(it) }
      color?.let { stream.writeInt32(it) }
      TlGen_Vector.serialize(stream, pinned_peers)
      TlGen_Vector.serialize(stream, include_peers)
      TlGen_Vector.serialize(stream, exclude_peers)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAA472651U
    }
  }

  public data class TL_dialogFilterChatlist(
    public val has_my_invites: Boolean,
    public val title_noanimate: Boolean,
    public val id: Int,
    public val title: TlGen_TextWithEntities,
    public val emoticon: String?,
    public val color: Int?,
    public val pinned_peers: List<TlGen_InputPeer>,
    public val include_peers: List<TlGen_InputPeer>,
  ) : TlGen_DialogFilter() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (emoticon != null) result = result or 33554432U
        if (has_my_invites) result = result or 67108864U
        if (color != null) result = result or 134217728U
        if (title_noanimate) result = result or 268435456U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      title.serializeToStream(stream)
      emoticon?.let { stream.writeString(it) }
      color?.let { stream.writeInt32(it) }
      TlGen_Vector.serialize(stream, pinned_peers)
      TlGen_Vector.serialize(stream, include_peers)
    }

    public companion object {
      public const val MAGIC: UInt = 0x96537BD7U
    }
  }
}
