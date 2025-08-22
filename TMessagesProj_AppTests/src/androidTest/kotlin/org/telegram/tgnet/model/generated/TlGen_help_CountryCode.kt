package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_CountryCode : TlGen_Object {
  public data class TL_help_countryCode(
    public val country_code: String,
    public val prefixes: List<String>?,
    public val patterns: List<String>?,
  ) : TlGen_help_CountryCode() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (prefixes != null) result = result or 1U
        if (patterns != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(country_code)
      prefixes?.let { TlGen_Vector.serializeString(stream, it) }
      patterns?.let { TlGen_Vector.serializeString(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4203C5EFU
    }
  }
}
