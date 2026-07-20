package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Birthday : TlGen_Object {
  public data class TL_birthday(
    public val day: Int,
    public val month: Int,
    public val year: Int?,
  ) : TlGen_Birthday() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (year != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(day)
      stream.writeInt32(month)
      year?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x6C8E1E06U
    }
  }
}
