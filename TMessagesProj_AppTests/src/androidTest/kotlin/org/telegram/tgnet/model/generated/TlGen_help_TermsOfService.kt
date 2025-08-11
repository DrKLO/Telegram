package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_TermsOfService : TlGen_Object {
  public data class TL_help_termsOfService(
    public val popup: Boolean,
    public val id: TlGen_DataJSON,
    public val text: String,
    public val entities: List<TlGen_MessageEntity>,
    public val min_age_confirm: Int?,
  ) : TlGen_help_TermsOfService() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (popup) result = result or 1U
        if (min_age_confirm != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      id.serializeToStream(stream)
      stream.writeString(text)
      TlGen_Vector.serialize(stream, entities)
      min_age_confirm?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x780A0310U
    }
  }
}
