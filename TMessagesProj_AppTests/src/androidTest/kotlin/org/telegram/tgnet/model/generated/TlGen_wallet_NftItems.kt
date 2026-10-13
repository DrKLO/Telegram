package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_NftItems : TlGen_Object {
  public data class TL_wallet_nftItems(
    public val items: List<TlGen_wallet_NftItem>,
    public val next_offset: String?,
  ) : TlGen_wallet_NftItems() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_offset != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, items)
      next_offset?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x794D486FU
    }
  }
}
