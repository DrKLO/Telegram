package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BotVerifierSettings : TlGen_Object {
  public data class TL_botVerifierSettings(
    public val can_modify_custom_description: Boolean,
    public val icon: Long,
    public val company: String,
    public val custom_description: String?,
  ) : TlGen_BotVerifierSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (custom_description != null) result = result or 1U
        if (can_modify_custom_description) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(icon)
      stream.writeString(company)
      custom_description?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB0CD6617U
    }
  }
}
