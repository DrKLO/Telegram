package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WalletTransaction : TlGen_Object {
  public data class TL_walletTransaction(
    public val incoming: Boolean,
    public val gasless: Boolean,
    public val failed: Boolean,
    public val key_change: Boolean,
    public val comment_encrypted: Boolean,
    public val id: String,
    public val amount: Long,
    public val fee: Long,
    public val date: Int,
    public val peer: TlGen_WalletTransactionPeer,
    public val comment: String?,
    public val tx_hash: String?,
    public val nft: TlGen_wallet_NftItem?,
  ) : TlGen_WalletTransaction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (incoming) result = result or 1U
        if (gasless) result = result or 2U
        if (failed) result = result or 4U
        if (comment != null) result = result or 8U
        if (tx_hash != null) result = result or 16U
        if (key_change) result = result or 32U
        if (comment_encrypted) result = result or 64U
        if (nft != null) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeInt64(amount)
      stream.writeInt64(fee)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      comment?.let { stream.writeString(it) }
      tx_hash?.let { stream.writeString(it) }
      nft?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x952DC143U
    }
  }
}
