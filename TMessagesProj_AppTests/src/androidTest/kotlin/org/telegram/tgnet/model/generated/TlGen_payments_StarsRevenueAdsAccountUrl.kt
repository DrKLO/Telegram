package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_StarsRevenueAdsAccountUrl : TlGen_Object {
  public data class TL_payments_starsRevenueAdsAccountUrl(
    public val url: String,
  ) : TlGen_payments_StarsRevenueAdsAccountUrl() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0x394E7F21U
    }
  }
}
