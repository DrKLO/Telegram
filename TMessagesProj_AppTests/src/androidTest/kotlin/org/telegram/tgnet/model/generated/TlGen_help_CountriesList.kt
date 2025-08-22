package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_CountriesList : TlGen_Object {
  public data object TL_help_countriesListNotModified : TlGen_help_CountriesList() {
    public const val MAGIC: UInt = 0x93CC1F32U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_help_countriesList(
    public val countries: List<TlGen_help_Country>,
    public val hash: Int,
  ) : TlGen_help_CountriesList() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, countries)
      stream.writeInt32(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x87D0759EU
    }
  }
}
