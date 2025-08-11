package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_TimezonesList : TlGen_Object {
  public data object TL_help_timezonesListNotModified : TlGen_help_TimezonesList() {
    public const val MAGIC: UInt = 0x970708CCU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_help_timezonesList(
    public val timezones: List<TlGen_Timezone>,
    public val hash: Int,
  ) : TlGen_help_TimezonesList() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, timezones)
      stream.writeInt32(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7B74ED71U
    }
  }
}
