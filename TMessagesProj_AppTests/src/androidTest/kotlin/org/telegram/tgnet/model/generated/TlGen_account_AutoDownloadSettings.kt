package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_AutoDownloadSettings : TlGen_Object {
  public data class TL_account_autoDownloadSettings(
    public val low: TlGen_AutoDownloadSettings,
    public val medium: TlGen_AutoDownloadSettings,
    public val high: TlGen_AutoDownloadSettings,
  ) : TlGen_account_AutoDownloadSettings() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      low.serializeToStream(stream)
      medium.serializeToStream(stream)
      high.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x63CACF26U
    }
  }
}
