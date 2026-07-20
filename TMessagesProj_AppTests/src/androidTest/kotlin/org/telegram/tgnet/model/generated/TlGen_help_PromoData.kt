package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_PromoData : TlGen_Object {
  public data class TL_help_promoDataEmpty(
    public val expires: Int,
  ) : TlGen_help_PromoData() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(expires)
    }

    public companion object {
      public const val MAGIC: UInt = 0x98F6AC75U
    }
  }

  public data class TL_help_promoData(
    public val proxy: Boolean,
    public val expires: Int,
    public val peer: TlGen_Peer?,
    public val psa_type: String?,
    public val psa_message: String?,
    public val pending_suggestions: List<String>,
    public val dismissed_suggestions: List<String>,
    public val custom_pending_suggestion: TlGen_PendingSuggestion?,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_help_PromoData() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (proxy) result = result or 1U
        if (psa_type != null) result = result or 2U
        if (psa_message != null) result = result or 4U
        if (peer != null) result = result or 8U
        if (custom_pending_suggestion != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(expires)
      peer?.serializeToStream(stream)
      psa_type?.let { stream.writeString(it) }
      psa_message?.let { stream.writeString(it) }
      TlGen_Vector.serializeString(stream, pending_suggestions)
      TlGen_Vector.serializeString(stream, dismissed_suggestions)
      custom_pending_suggestion?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x08A4D87AU
    }
  }
}
