package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_Country : TlGen_Object {
  public data class TL_help_country(
    public val hidden: Boolean,
    public val iso2: String,
    public val default_name: String,
    public val name: String?,
    public val country_codes: List<TlGen_help_CountryCode>,
  ) : TlGen_help_Country() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (hidden) result = result or 1U
        if (name != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(iso2)
      stream.writeString(default_name)
      name?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, country_codes)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC3878E23U
    }
  }
}
