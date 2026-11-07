package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WidgetQueryType : TlGen_Object {
  public data class TL_widgetQueryTypeData(
    public val with_wallet: Boolean,
  ) : TlGen_WidgetQueryType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (with_wallet) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x16D8CF0BU
    }
  }

  public data class TL_widgetQueryTypeSignData(
    public val wallet: TlGen_UserWallet,
    public val sign_data: TlGen_DataJSON,
  ) : TlGen_WidgetQueryType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      wallet.serializeToStream(stream)
      sign_data.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x192047DCU
    }
  }

  public data class TL_widgetQueryTypeSentTransaction(
    public val wallet: TlGen_UserWallet,
    public val msg_hash: String,
  ) : TlGen_WidgetQueryType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      wallet.serializeToStream(stream)
      stream.writeString(msg_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFF3B33B4U
    }
  }
}
