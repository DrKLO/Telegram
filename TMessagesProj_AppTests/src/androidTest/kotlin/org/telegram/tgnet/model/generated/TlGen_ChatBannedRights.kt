package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChatBannedRights : TlGen_Object {
  public data class TL_chatBannedRights(
    public val view_messages: Boolean,
    public val send_messages: Boolean,
    public val send_media: Boolean,
    public val send_stickers: Boolean,
    public val send_gifs: Boolean,
    public val send_games: Boolean,
    public val send_inline: Boolean,
    public val embed_links: Boolean,
    public val send_polls: Boolean,
    public val change_info: Boolean,
    public val invite_users: Boolean,
    public val pin_messages: Boolean,
    public val manage_topics: Boolean,
    public val send_photos: Boolean,
    public val send_videos: Boolean,
    public val send_roundvideos: Boolean,
    public val send_audios: Boolean,
    public val send_voices: Boolean,
    public val send_docs: Boolean,
    public val send_plain: Boolean,
    public val until_date: Int,
  ) : TlGen_ChatBannedRights() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (view_messages) result = result or 1U
        if (send_messages) result = result or 2U
        if (send_media) result = result or 4U
        if (send_stickers) result = result or 8U
        if (send_gifs) result = result or 16U
        if (send_games) result = result or 32U
        if (send_inline) result = result or 64U
        if (embed_links) result = result or 128U
        if (send_polls) result = result or 256U
        if (change_info) result = result or 1024U
        if (invite_users) result = result or 32768U
        if (pin_messages) result = result or 131072U
        if (manage_topics) result = result or 262144U
        if (send_photos) result = result or 524288U
        if (send_videos) result = result or 1048576U
        if (send_roundvideos) result = result or 2097152U
        if (send_audios) result = result or 4194304U
        if (send_voices) result = result or 8388608U
        if (send_docs) result = result or 16777216U
        if (send_plain) result = result or 33554432U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(until_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9F120418U
    }
  }
}
