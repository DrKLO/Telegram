package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SponsoredMessage : TlGen_Object {
  public data class TL_sponsoredMessage(
    public val recommended: Boolean,
    public val can_report: Boolean,
    public val random_id: List<Byte>,
    public val url: String,
    public val title: String,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val photo: TlGen_Photo?,
    public val media: TlGen_MessageMedia?,
    public val color: TlGen_PeerColor?,
    public val button_text: String,
    public val sponsor_info: String?,
    public val additional_info: String?,
    public val multiflags_15: Multiflags_15?,
  ) : TlGen_SponsoredMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (entities != null) result = result or 2U
        if (recommended) result = result or 32U
        if (photo != null) result = result or 64U
        if (sponsor_info != null) result = result or 128U
        if (additional_info != null) result = result or 256U
        if (can_report) result = result or 4096U
        if (color != null) result = result or 8192U
        if (media != null) result = result or 16384U
        if (multiflags_15 != null) result = result or 32768U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeByteArray(random_id.toByteArray())
      stream.writeString(url)
      stream.writeString(title)
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      photo?.serializeToStream(stream)
      media?.serializeToStream(stream)
      color?.serializeToStream(stream)
      stream.writeString(button_text)
      sponsor_info?.let { stream.writeString(it) }
      additional_info?.let { stream.writeString(it) }
      multiflags_15?.let { stream.writeInt32(it.min_display_duration) }
      multiflags_15?.let { stream.writeInt32(it.max_display_duration) }
    }

    public data class Multiflags_15(
      public val min_display_duration: Int,
      public val max_display_duration: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x7DBF8673U
    }
  }
}
