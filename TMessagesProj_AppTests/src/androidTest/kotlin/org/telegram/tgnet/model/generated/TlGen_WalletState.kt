package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WalletState : TlGen_Object {
  public data class TL_walletStateEmpty(
    public val creating: Boolean,
  ) : TlGen_WalletState() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creating) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x9CB9B2ECU
    }
  }

  public data class TL_walletState(
    public val backup_enabled: Boolean,
    public val can_export_phrase: Boolean,
    public val can_enable_backup: Boolean,
    public val address: String,
    public val public_key: List<Byte>,
    public val balance: Long,
  ) : TlGen_WalletState() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (backup_enabled) result = result or 1U
        if (can_export_phrase) result = result or 2U
        if (can_enable_backup) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(address)
      stream.writeByteArray(public_key.toByteArray())
      stream.writeInt64(balance)
    }

    public companion object {
      public const val MAGIC: UInt = 0x95C5346BU
    }
  }
}
