package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StoryItem : TlGen_Object {
  public data class TL_storyItemDeleted(
    public val id: Int,
  ) : TlGen_StoryItem() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x51E6EE4FU
    }
  }

  public data class TL_storyItemSkipped(
    public val close_friends: Boolean,
    public val live: Boolean,
    public val id: Int,
    public val date: Int,
    public val expire_date: Int,
  ) : TlGen_StoryItem() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (close_friends) result = result or 256U
        if (live) result = result or 512U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(date)
      stream.writeInt32(expire_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFFADC913U
    }
  }

  public data class TL_storyItem(
    public val pinned: Boolean,
    public val `public`: Boolean,
    public val close_friends: Boolean,
    public val min: Boolean,
    public val noforwards: Boolean,
    public val edited: Boolean,
    public val contacts: Boolean,
    public val selected_contacts: Boolean,
    public val `out`: Boolean,
    public val id: Int,
    public val date: Int,
    public val from_id: TlGen_Peer?,
    public val fwd_from: TlGen_StoryFwdHeader?,
    public val expire_date: Int,
    public val caption: String?,
    public val entities: List<TlGen_MessageEntity>?,
    public val media: TlGen_MessageMedia,
    public val media_areas: List<TlGen_MediaArea>?,
    public val privacy: List<TlGen_PrivacyRule>?,
    public val views: TlGen_StoryViews?,
    public val sent_reaction: TlGen_Reaction?,
    public val albums: List<Int>?,
  ) : TlGen_StoryItem() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (caption != null) result = result or 1U
        if (entities != null) result = result or 2U
        if (privacy != null) result = result or 4U
        if (views != null) result = result or 8U
        if (pinned) result = result or 32U
        if (public) result = result or 128U
        if (close_friends) result = result or 256U
        if (min) result = result or 512U
        if (noforwards) result = result or 1024U
        if (edited) result = result or 2048U
        if (contacts) result = result or 4096U
        if (selected_contacts) result = result or 8192U
        if (media_areas != null) result = result or 16384U
        if (sent_reaction != null) result = result or 32768U
        if (out) result = result or 65536U
        if (fwd_from != null) result = result or 131072U
        if (from_id != null) result = result or 262144U
        if (albums != null) result = result or 524288U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(date)
      from_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      stream.writeInt32(expire_date)
      caption?.let { stream.writeString(it) }
      entities?.let { TlGen_Vector.serialize(stream, it) }
      media.serializeToStream(stream)
      media_areas?.let { TlGen_Vector.serialize(stream, it) }
      privacy?.let { TlGen_Vector.serialize(stream, it) }
      views?.serializeToStream(stream)
      sent_reaction?.serializeToStream(stream)
      albums?.let { TlGen_Vector.serializeInt(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xEDF164F1U
    }
  }

  public data class TL_storyItem_layer160(
    public val pinned: Boolean,
    public val `public`: Boolean,
    public val close_friends: Boolean,
    public val min: Boolean,
    public val noforwards: Boolean,
    public val edited: Boolean,
    public val contacts: Boolean,
    public val selected_contacts: Boolean,
    public val id: Int,
    public val date: Int,
    public val expire_date: Int,
    public val caption: String?,
    public val entities: List<TlGen_MessageEntity>?,
    public val media: TlGen_MessageMedia,
    public val privacy: List<TlGen_PrivacyRule>?,
    public val views: TlGen_StoryViews?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (caption != null) result = result or 1U
        if (entities != null) result = result or 2U
        if (privacy != null) result = result or 4U
        if (views != null) result = result or 8U
        if (pinned) result = result or 32U
        if (public) result = result or 128U
        if (close_friends) result = result or 256U
        if (min) result = result or 512U
        if (noforwards) result = result or 1024U
        if (edited) result = result or 2048U
        if (contacts) result = result or 4096U
        if (selected_contacts) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(date)
      stream.writeInt32(expire_date)
      caption?.let { stream.writeString(it) }
      entities?.let { TlGen_Vector.serialize(stream, it) }
      media.serializeToStream(stream)
      privacy?.let { TlGen_Vector.serialize(stream, it) }
      views?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x562AA637U
    }
  }

  public data class TL_storyItem_layer173(
    public val pinned: Boolean,
    public val `public`: Boolean,
    public val close_friends: Boolean,
    public val min: Boolean,
    public val noforwards: Boolean,
    public val edited: Boolean,
    public val contacts: Boolean,
    public val selected_contacts: Boolean,
    public val `out`: Boolean,
    public val id: Int,
    public val date: Int,
    public val fwd_from: TlGen_StoryFwdHeader?,
    public val expire_date: Int,
    public val caption: String?,
    public val entities: List<TlGen_MessageEntity>?,
    public val media: TlGen_MessageMedia,
    public val media_areas: List<TlGen_MediaArea>?,
    public val privacy: List<TlGen_PrivacyRule>?,
    public val views: TlGen_StoryViews?,
    public val sent_reaction: TlGen_Reaction?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (caption != null) result = result or 1U
        if (entities != null) result = result or 2U
        if (privacy != null) result = result or 4U
        if (views != null) result = result or 8U
        if (pinned) result = result or 32U
        if (public) result = result or 128U
        if (close_friends) result = result or 256U
        if (min) result = result or 512U
        if (noforwards) result = result or 1024U
        if (edited) result = result or 2048U
        if (contacts) result = result or 4096U
        if (selected_contacts) result = result or 8192U
        if (media_areas != null) result = result or 16384U
        if (sent_reaction != null) result = result or 32768U
        if (out) result = result or 65536U
        if (fwd_from != null) result = result or 131072U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(date)
      fwd_from?.serializeToStream(stream)
      stream.writeInt32(expire_date)
      caption?.let { stream.writeString(it) }
      entities?.let { TlGen_Vector.serialize(stream, it) }
      media.serializeToStream(stream)
      media_areas?.let { TlGen_Vector.serialize(stream, it) }
      privacy?.let { TlGen_Vector.serialize(stream, it) }
      views?.serializeToStream(stream)
      sent_reaction?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAF6365A1U
    }
  }

  public data class TL_storyItem_layer166(
    public val pinned: Boolean,
    public val `public`: Boolean,
    public val close_friends: Boolean,
    public val min: Boolean,
    public val noforwards: Boolean,
    public val edited: Boolean,
    public val contacts: Boolean,
    public val selected_contacts: Boolean,
    public val `out`: Boolean,
    public val id: Int,
    public val date: Int,
    public val expire_date: Int,
    public val caption: String?,
    public val entities: List<TlGen_MessageEntity>?,
    public val media: TlGen_MessageMedia,
    public val media_areas: List<TlGen_MediaArea>?,
    public val privacy: List<TlGen_PrivacyRule>?,
    public val views: TlGen_StoryViews?,
    public val sent_reaction: TlGen_Reaction?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (caption != null) result = result or 1U
        if (entities != null) result = result or 2U
        if (privacy != null) result = result or 4U
        if (views != null) result = result or 8U
        if (pinned) result = result or 32U
        if (public) result = result or 128U
        if (close_friends) result = result or 256U
        if (min) result = result or 512U
        if (noforwards) result = result or 1024U
        if (edited) result = result or 2048U
        if (contacts) result = result or 4096U
        if (selected_contacts) result = result or 8192U
        if (media_areas != null) result = result or 16384U
        if (sent_reaction != null) result = result or 32768U
        if (out) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(date)
      stream.writeInt32(expire_date)
      caption?.let { stream.writeString(it) }
      entities?.let { TlGen_Vector.serialize(stream, it) }
      media.serializeToStream(stream)
      media_areas?.let { TlGen_Vector.serialize(stream, it) }
      privacy?.let { TlGen_Vector.serialize(stream, it) }
      views?.serializeToStream(stream)
      sent_reaction?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x44C457CEU
    }
  }

  public data class TL_storyItem_layer210(
    public val pinned: Boolean,
    public val `public`: Boolean,
    public val close_friends: Boolean,
    public val min: Boolean,
    public val noforwards: Boolean,
    public val edited: Boolean,
    public val contacts: Boolean,
    public val selected_contacts: Boolean,
    public val `out`: Boolean,
    public val id: Int,
    public val date: Int,
    public val from_id: TlGen_Peer?,
    public val fwd_from: TlGen_StoryFwdHeader?,
    public val expire_date: Int,
    public val caption: String?,
    public val entities: List<TlGen_MessageEntity>?,
    public val media: TlGen_MessageMedia,
    public val media_areas: List<TlGen_MediaArea>?,
    public val privacy: List<TlGen_PrivacyRule>?,
    public val views: TlGen_StoryViews?,
    public val sent_reaction: TlGen_Reaction?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (caption != null) result = result or 1U
        if (entities != null) result = result or 2U
        if (privacy != null) result = result or 4U
        if (views != null) result = result or 8U
        if (pinned) result = result or 32U
        if (public) result = result or 128U
        if (close_friends) result = result or 256U
        if (min) result = result or 512U
        if (noforwards) result = result or 1024U
        if (edited) result = result or 2048U
        if (contacts) result = result or 4096U
        if (selected_contacts) result = result or 8192U
        if (media_areas != null) result = result or 16384U
        if (sent_reaction != null) result = result or 32768U
        if (out) result = result or 65536U
        if (fwd_from != null) result = result or 131072U
        if (from_id != null) result = result or 262144U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(date)
      from_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      stream.writeInt32(expire_date)
      caption?.let { stream.writeString(it) }
      entities?.let { TlGen_Vector.serialize(stream, it) }
      media.serializeToStream(stream)
      media_areas?.let { TlGen_Vector.serialize(stream, it) }
      privacy?.let { TlGen_Vector.serialize(stream, it) }
      views?.serializeToStream(stream)
      sent_reaction?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x79B26A24U
    }
  }
}
