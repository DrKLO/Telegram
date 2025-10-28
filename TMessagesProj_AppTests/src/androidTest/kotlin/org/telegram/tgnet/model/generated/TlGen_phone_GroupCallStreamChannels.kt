package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_phone_GroupCallStreamChannels : TlGen_Object {
  public data class TL_phone_groupCallStreamChannels(
    public val channels: List<TlGen_GroupCallStreamChannel>,
  ) : TlGen_phone_GroupCallStreamChannels() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, channels)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD0E482B2U
    }
  }
}
