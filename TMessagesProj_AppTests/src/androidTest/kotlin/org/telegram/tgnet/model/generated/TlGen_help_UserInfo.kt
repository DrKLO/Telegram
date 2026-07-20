package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_UserInfo : TlGen_Object {
  public data object TL_help_userInfoEmpty : TlGen_help_UserInfo() {
    public const val MAGIC: UInt = 0xF3AE2EEDU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_help_userInfo(
    public val message: String,
    public val entities: List<TlGen_MessageEntity>,
    public val author: String,
    public val date: Int,
  ) : TlGen_help_UserInfo() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(message)
      TlGen_Vector.serialize(stream, entities)
      stream.writeString(author)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x01EB3758U
    }
  }
}
