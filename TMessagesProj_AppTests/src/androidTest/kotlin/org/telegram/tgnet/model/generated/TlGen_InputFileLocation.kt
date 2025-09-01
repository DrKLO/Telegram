package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputFileLocation : TlGen_Object {
  public data class TL_inputEncryptedFileLocation(
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputFileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF5235D55U
    }
  }

  public data class TL_inputSecureFileLocation(
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputFileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCBC7EE28U
    }
  }

  public data class TL_inputFileLocation(
    public val volume_id: Long,
    public val local_id: Int,
    public val secret: Long,
    public val file_reference: List<Byte>,
  ) : TlGen_InputFileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(volume_id)
      stream.writeInt32(local_id)
      stream.writeInt64(secret)
      stream.writeByteArray(file_reference.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xDFDAABE1U
    }
  }

  public data class TL_inputDocumentFileLocation(
    public val id: Long,
    public val access_hash: Long,
    public val file_reference: List<Byte>,
    public val thumb_size: String,
  ) : TlGen_InputFileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeByteArray(file_reference.toByteArray())
      stream.writeString(thumb_size)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBAD07584U
    }
  }

  public data class TL_inputPhotoFileLocation(
    public val id: Long,
    public val access_hash: Long,
    public val file_reference: List<Byte>,
    public val thumb_size: String,
  ) : TlGen_InputFileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeByteArray(file_reference.toByteArray())
      stream.writeString(thumb_size)
    }

    public companion object {
      public const val MAGIC: UInt = 0x40181FFEU
    }
  }

  public data class TL_inputPeerPhotoFileLocation(
    public val big: Boolean,
    public val peer: TlGen_InputPeer,
    public val photo_id: Long,
  ) : TlGen_InputFileLocation() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (big) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt64(photo_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x37257E99U
    }
  }

  public data class TL_inputStickerSetThumb(
    public val stickerset: TlGen_InputStickerSet,
    public val thumb_version: Int,
  ) : TlGen_InputFileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stickerset.serializeToStream(stream)
      stream.writeInt32(thumb_version)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9D84F3DBU
    }
  }

  public data class TL_inputGroupCallStream(
    public val call: TlGen_InputGroupCall,
    public val time_ms: Long,
    public val scale: Int,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_InputFileLocation() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      call.serializeToStream(stream)
      stream.writeInt64(time_ms)
      stream.writeInt32(scale)
      multiflags_0?.let { stream.writeInt32(it.video_channel) }
      multiflags_0?.let { stream.writeInt32(it.video_quality) }
    }

    public data class Multiflags_0(
      public val video_channel: Int,
      public val video_quality: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x0598A92AU
    }
  }
}
