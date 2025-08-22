package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_RequirementToContact : TlGen_Object {
  public data object TL_requirementToContactEmpty : TlGen_RequirementToContact() {
    public const val MAGIC: UInt = 0x050A9839U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_requirementToContactPremium : TlGen_RequirementToContact() {
    public const val MAGIC: UInt = 0xE581E4E9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_requirementToContactPaidMessages(
    public val stars_amount: Long,
  ) : TlGen_RequirementToContact() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(stars_amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB4F67E93U
    }
  }
}
