package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_OnrampMethodAvailability : TlGen_Object {
  public data class TL_onrampMethodAvailability(
    public val available: Boolean,
    public val payment_method: String,
  ) : TlGen_OnrampMethodAvailability() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (available) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(payment_method)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9EC8C6A8U
    }
  }
}
