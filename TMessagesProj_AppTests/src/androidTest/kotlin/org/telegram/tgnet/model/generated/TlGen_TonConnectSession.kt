package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_TonConnectSession : TlGen_Object {
  public data class TL_tonConnectSession(
    public val pending: Boolean,
    public val closing: Boolean,
    public val closed: Boolean,
    public val id: Long,
    public val dapp_client_id: String,
    public val client_id: String?,
    public val nonce: List<Byte>,
    public val manifest: TlGen_TonConnectManifest?,
    public val manifest_error: Int?,
    public val date: Int,
  ) : TlGen_TonConnectSession() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pending) result = result or 1U
        if (closing) result = result or 2U
        if (closed) result = result or 4U
        if (client_id != null) result = result or 8U
        if (manifest != null) result = result or 16U
        if (manifest_error != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(dapp_client_id)
      client_id?.let { stream.writeString(it) }
      stream.writeByteArray(nonce.toByteArray())
      manifest?.serializeToStream(stream)
      manifest_error?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x126556C6U
    }
  }
}
