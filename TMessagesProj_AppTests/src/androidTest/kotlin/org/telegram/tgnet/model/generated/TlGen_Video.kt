package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Video : TlGen_Object {
  public data class TL_videoEmpty_layer46(
    public val id: Long,
  ) : TlGen_Video() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC10658A8U
    }
  }

  public data class TL_video_layer12(
    public val id: Long,
    public val access_hash: Long,
    public val user_id: Int,
    public val date: Int,
    public val caption: String,
    public val duration: Int,
    public val size: Int,
    public val thumb: TlGen_PhotoSize,
    public val dc_id: Int,
    public val w: Int,
    public val h: Int,
  ) : TlGen_Video() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(user_id)
      stream.writeInt32(date)
      stream.writeString(caption)
      stream.writeInt32(duration)
      stream.writeInt32(size)
      thumb.serializeToStream(stream)
      stream.writeInt32(dc_id)
      stream.writeInt32(w)
      stream.writeInt32(h)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5A04A49FU
    }
  }

  public data class TL_video_layer27(
    public val id: Long,
    public val access_hash: Long,
    public val user_id: Int,
    public val date: Int,
    public val caption: String,
    public val duration: Int,
    public val mime_type: String,
    public val size: Int,
    public val thumb: TlGen_PhotoSize,
    public val dc_id: Int,
    public val w: Int,
    public val h: Int,
  ) : TlGen_Video() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(user_id)
      stream.writeInt32(date)
      stream.writeString(caption)
      stream.writeInt32(duration)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      thumb.serializeToStream(stream)
      stream.writeInt32(dc_id)
      stream.writeInt32(w)
      stream.writeInt32(h)
    }

    public companion object {
      public const val MAGIC: UInt = 0x388FA391U
    }
  }

  public data class TL_video_layer32(
    public val id: Long,
    public val access_hash: Long,
    public val user_id: Int,
    public val date: Int,
    public val duration: Int,
    public val size: Int,
    public val thumb: TlGen_PhotoSize,
    public val dc_id: Int,
    public val w: Int,
    public val h: Int,
  ) : TlGen_Video() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(user_id)
      stream.writeInt32(date)
      stream.writeInt32(duration)
      stream.writeInt32(size)
      thumb.serializeToStream(stream)
      stream.writeInt32(dc_id)
      stream.writeInt32(w)
      stream.writeInt32(h)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEE9F4A4DU
    }
  }

  public data class TL_video_layer46(
    public val id: Long,
    public val access_hash: Long,
    public val date: Int,
    public val duration: Int,
    public val mime_type: String,
    public val size: Int,
    public val thumb: TlGen_PhotoSize,
    public val dc_id: Int,
    public val w: Int,
    public val h: Int,
  ) : TlGen_Video() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      stream.writeInt32(duration)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      thumb.serializeToStream(stream)
      stream.writeInt32(dc_id)
      stream.writeInt32(w)
      stream.writeInt32(h)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF72887D3U
    }
  }
}
