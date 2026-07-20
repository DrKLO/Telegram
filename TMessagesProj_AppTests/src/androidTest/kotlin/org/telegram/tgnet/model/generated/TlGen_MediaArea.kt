package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MediaArea : TlGen_Object {
  public data class TL_mediaAreaVenue(
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val geo: TlGen_GeoPoint,
    public val title: String,
    public val address: String,
    public val provider: String,
    public val venue_id: String,
    public val venue_type: String,
  ) : TlGen_MediaArea() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      coordinates.serializeToStream(stream)
      geo.serializeToStream(stream)
      stream.writeString(title)
      stream.writeString(address)
      stream.writeString(provider)
      stream.writeString(venue_id)
      stream.writeString(venue_type)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBE82DB9CU
    }
  }

  public data class TL_inputMediaAreaVenue(
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val query_id: Long,
    public val result_id: String,
  ) : TlGen_MediaArea() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      coordinates.serializeToStream(stream)
      stream.writeInt64(query_id)
      stream.writeString(result_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB282217FU
    }
  }

  public data class TL_mediaAreaSuggestedReaction(
    public val dark: Boolean,
    public val flipped: Boolean,
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val reaction: TlGen_Reaction,
  ) : TlGen_MediaArea() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (dark) result = result or 1U
        if (flipped) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      coordinates.serializeToStream(stream)
      reaction.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x14455871U
    }
  }

  public data class TL_mediaAreaChannelPost(
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val channel_id: Long,
    public val msg_id: Int,
  ) : TlGen_MediaArea() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      coordinates.serializeToStream(stream)
      stream.writeInt64(channel_id)
      stream.writeInt32(msg_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x770416AFU
    }
  }

  public data class TL_inputMediaAreaChannelPost(
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val channel: TlGen_InputChannel,
    public val msg_id: Int,
  ) : TlGen_MediaArea() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      coordinates.serializeToStream(stream)
      channel.serializeToStream(stream)
      stream.writeInt32(msg_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2271F2BFU
    }
  }

  public data class TL_mediaAreaGeoPoint(
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val geo: TlGen_GeoPoint,
    public val address: TlGen_GeoPointAddress?,
  ) : TlGen_MediaArea() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (address != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      coordinates.serializeToStream(stream)
      geo.serializeToStream(stream)
      address?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCAD5452DU
    }
  }

  public data class TL_mediaAreaUrl(
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val url: String,
  ) : TlGen_MediaArea() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      coordinates.serializeToStream(stream)
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0x37381085U
    }
  }

  public data class TL_mediaAreaWeather(
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val emoji: String,
    public val temperature_c: Double,
    public val color: Int,
  ) : TlGen_MediaArea() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      coordinates.serializeToStream(stream)
      stream.writeString(emoji)
      stream.writeDouble(temperature_c)
      stream.writeInt32(color)
    }

    public companion object {
      public const val MAGIC: UInt = 0x49A6549CU
    }
  }

  public data class TL_mediaAreaStarGift(
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val slug: String,
  ) : TlGen_MediaArea() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      coordinates.serializeToStream(stream)
      stream.writeString(slug)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5787686DU
    }
  }

  public data class TL_mediaAreaGeoPoint_layer181(
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val geo: TlGen_GeoPoint,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      coordinates.serializeToStream(stream)
      geo.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDF8B3B22U
    }
  }
}
