package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_FileLocation : TlGen_Object {
  public data class TL_fileLocationUnavailable_layer97(
    public val volume_id: Long,
    public val local_id: Int,
    public val secret: Long,
  ) : TlGen_FileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(volume_id)
      stream.writeInt32(local_id)
      stream.writeInt64(secret)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7C596B46U
    }
  }

  public data class TL_fileLocation_layer85(
    public val dc_id: Int,
    public val volume_id: Long,
    public val local_id: Int,
    public val secret: Long,
  ) : TlGen_FileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(dc_id)
      stream.writeInt64(volume_id)
      stream.writeInt32(local_id)
      stream.writeInt64(secret)
    }

    public companion object {
      public const val MAGIC: UInt = 0x53D69076U
    }
  }

  public data class TL_fileLocation_layer97(
    public val dc_id: Int,
    public val volume_id: Long,
    public val local_id: Int,
    public val secret: Long,
    public val file_reference: List<Byte>,
  ) : TlGen_FileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(dc_id)
      stream.writeInt64(volume_id)
      stream.writeInt32(local_id)
      stream.writeInt64(secret)
      stream.writeByteArray(file_reference.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x091D11EBU
    }
  }

  public data class TL_fileLocationToBeDeprecated_layer127(
    public val volume_id: Long,
    public val local_id: Int,
  ) : TlGen_FileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(volume_id)
      stream.writeInt32(local_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBC7FC6CDU
    }
  }
}
