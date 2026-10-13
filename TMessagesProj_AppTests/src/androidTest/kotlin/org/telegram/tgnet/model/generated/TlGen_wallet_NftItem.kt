package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_NftItem : TlGen_Object {
  public data class TL_wallet_nftItem(
    public val collection_address: String?,
    public val address: String,
    public val owner_address: String,
    public val index: String,
    public val name: String?,
    public val description: String?,
    public val image: TlGen_WebDocument?,
    public val image_small: TlGen_WebDocument?,
    public val content_url: TlGen_WebDocument?,
    public val lottie: TlGen_WebDocument?,
    public val attributes: List<TlGen_wallet_NftAttribute>?,
    public val extra: TlGen_DataJSON?,
  ) : TlGen_wallet_NftItem() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (collection_address != null) result = result or 1U
        if (name != null) result = result or 2U
        if (description != null) result = result or 4U
        if (image != null) result = result or 8U
        if (image_small != null) result = result or 16U
        if (content_url != null) result = result or 32U
        if (lottie != null) result = result or 64U
        if (attributes != null) result = result or 128U
        if (extra != null) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      collection_address?.let { stream.writeString(it) }
      stream.writeString(address)
      stream.writeString(owner_address)
      stream.writeString(index)
      name?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      image?.serializeToStream(stream)
      image_small?.serializeToStream(stream)
      content_url?.serializeToStream(stream)
      lottie?.serializeToStream(stream)
      attributes?.let { TlGen_Vector.serialize(stream, it) }
      extra?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3441FD1CU
    }
  }
}
