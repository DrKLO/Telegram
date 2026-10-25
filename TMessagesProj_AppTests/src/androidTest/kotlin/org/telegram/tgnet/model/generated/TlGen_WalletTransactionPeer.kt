package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WalletTransactionPeer : TlGen_Object {
  public data class TL_walletTransactionPeerUser(
    public val user_id: Long,
    public val address: String,
    public val domain: String?,
  ) : TlGen_WalletTransactionPeer() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (domain != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(user_id)
      stream.writeString(address)
      domain?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xD4EA706DU
    }
  }

  public data class TL_walletTransactionPeerAddress(
    public val address: String,
    public val domain: String?,
  ) : TlGen_WalletTransactionPeer() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (domain != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(address)
      domain?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x062CC1BCU
    }
  }

  public data class TL_walletTransactionPeerOnramp(
    public val address: String,
    public val domain: String?,
    public val provider_name: String,
  ) : TlGen_WalletTransactionPeer() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (domain != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(address)
      domain?.let { stream.writeString(it) }
      stream.writeString(provider_name)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE595186EU
    }
  }

  public data object TL_walletTransactionPeerUnsupported : TlGen_WalletTransactionPeer() {
    public const val MAGIC: UInt = 0x728BED5AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_walletTransactionPeerChannel(
    public val channel_id: Long,
    public val address: String,
    public val domain: String?,
  ) : TlGen_WalletTransactionPeer() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (domain != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(channel_id)
      stream.writeString(address)
      domain?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x54E70C31U
    }
  }

  public data class TL_walletTransactionPeerTelegramGift(
    public val own_profile: Boolean,
  ) : TlGen_WalletTransactionPeer() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (own_profile) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0xF4729499U
    }
  }
}
