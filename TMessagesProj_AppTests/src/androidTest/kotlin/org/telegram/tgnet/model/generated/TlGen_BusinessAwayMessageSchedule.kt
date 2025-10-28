package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BusinessAwayMessageSchedule : TlGen_Object {
  public data object TL_businessAwayMessageScheduleAlways : TlGen_BusinessAwayMessageSchedule() {
    public const val MAGIC: UInt = 0xC9B9E2B9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_businessAwayMessageScheduleOutsideWorkHours :
      TlGen_BusinessAwayMessageSchedule() {
    public const val MAGIC: UInt = 0xC3F2F501U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_businessAwayMessageScheduleCustom(
    public val start_date: Int,
    public val end_date: Int,
  ) : TlGen_BusinessAwayMessageSchedule() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(start_date)
      stream.writeInt32(end_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCC4D9ECCU
    }
  }
}
