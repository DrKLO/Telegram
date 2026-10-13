package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_OnrampProviderInfo : TlGen_Object {
  public data class TL_onrampProviderInfo(
    public val supports_base_currencies: Boolean,
    public val supports_limits: Boolean,
    public val supports_quote: Boolean,
    public val id: String,
    public val name: String,
    public val crypto_currencies: List<String>,
  ) : TlGen_OnrampProviderInfo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (supports_base_currencies) result = result or 1U
        if (supports_limits) result = result or 2U
        if (supports_quote) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeString(name)
      TlGen_Vector.serializeString(stream, crypto_currencies)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0DC27582U
    }
  }
}
