package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Audio : TlGen_Object {
  public data class TL_audioEmpty_layer45(
    public val id: Long,
  ) : TlGen_Audio() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x586988D8U
    }
  }

  public data class TL_audio_layer12(
    public val id: Long,
    public val access_hash: Long,
    public val user_id: Int,
    public val date: Int,
    public val duration: Int,
    public val size: Int,
    public val dc_id: Int,
  ) : TlGen_Audio() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(user_id)
      stream.writeInt32(date)
      stream.writeInt32(duration)
      stream.writeInt32(size)
      stream.writeInt32(dc_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x427425E7U
    }
  }

  public data class TL_audio_layer32(
    public val id: Long,
    public val access_hash: Long,
    public val user_id: Int,
    public val date: Int,
    public val duration: Int,
    public val mime_type: String,
    public val size: Int,
    public val dc_id: Int,
  ) : TlGen_Audio() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(user_id)
      stream.writeInt32(date)
      stream.writeInt32(duration)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      stream.writeInt32(dc_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC7AC6496U
    }
  }

  public data class TL_audio_layer45(
    public val id: Long,
    public val access_hash: Long,
    public val date: Int,
    public val duration: Int,
    public val mime_type: String,
    public val size: Int,
    public val dc_id: Int,
  ) : TlGen_Audio() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      stream.writeInt32(duration)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      stream.writeInt32(dc_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF9E35055U
    }
  }
}
