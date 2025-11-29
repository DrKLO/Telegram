package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_TodoCompletion : TlGen_Object {
  public data class TL_todoCompletion(
    public val id: Int,
    public val completed_by: TlGen_Peer,
    public val date: Int,
  ) : TlGen_TodoCompletion() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      completed_by.serializeToStream(stream)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x221BB5E4U
    }
  }

  public data class TL_todoCompletion_layer216(
    public val id: Int,
    public val completed_by: Long,
    public val date: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeInt64(completed_by)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4CC120B7U
    }
  }
}
