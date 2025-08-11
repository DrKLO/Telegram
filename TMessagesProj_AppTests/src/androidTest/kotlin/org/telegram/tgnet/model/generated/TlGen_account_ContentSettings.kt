package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_ContentSettings : TlGen_Object {
  public data class TL_account_contentSettings(
    public val sensitive_enabled: Boolean,
    public val sensitive_can_change: Boolean,
  ) : TlGen_account_ContentSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (sensitive_enabled) result = result or 1U
        if (sensitive_can_change) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x57E28221U
    }
  }
}
