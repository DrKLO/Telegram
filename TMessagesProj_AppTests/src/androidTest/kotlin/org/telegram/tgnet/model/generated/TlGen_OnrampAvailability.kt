package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_OnrampAvailability : TlGen_Object {
  public data class TL_onrampAvailability(
    public val allowed: Boolean,
    public val buy_allowed: Boolean,
    public val country_code: String,
    public val state: String?,
    public val methods: List<TlGen_OnrampMethodAvailability>,
  ) : TlGen_OnrampAvailability() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (allowed) result = result or 1U
        if (buy_allowed) result = result or 2U
        if (state != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(country_code)
      state?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, methods)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF9C84973U
    }
  }
}
