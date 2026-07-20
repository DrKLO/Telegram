package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_FactCheck : TlGen_Object {
  public data class TL_factCheck(
    public val need_check: Boolean,
    public val hash: Long,
    public val multiflags_1: Multiflags_1?,
  ) : TlGen_FactCheck() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (need_check) result = result or 1U
        if (multiflags_1 != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      multiflags_1?.let { stream.writeString(it.country) }
      multiflags_1?.let { it.text.serializeToStream(stream) }
      stream.writeInt64(hash)
    }

    public data class Multiflags_1(
      public val country: String,
      public val text: TlGen_TextWithEntities,
    )

    public companion object {
      public const val MAGIC: UInt = 0xB89BFCCFU
    }
  }
}
