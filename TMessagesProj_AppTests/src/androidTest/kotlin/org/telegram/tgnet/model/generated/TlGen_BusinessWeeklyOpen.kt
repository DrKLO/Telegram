package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_BusinessWeeklyOpen : TlGen_Object {
  public data class TL_businessWeeklyOpen(
    public val start_minute: Int,
    public val end_minute: Int,
  ) : TlGen_BusinessWeeklyOpen() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(start_minute)
      stream.writeInt32(end_minute)
    }

    public companion object {
      public const val MAGIC: UInt = 0x120B1AB9U
    }
  }
}
