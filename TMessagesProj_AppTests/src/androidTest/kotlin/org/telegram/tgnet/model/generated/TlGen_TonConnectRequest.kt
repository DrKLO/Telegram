package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_TonConnectRequest : TlGen_Object {
  public data class TL_tonConnectRequest(
    public val session_id: Long,
    public val msg_id: Int,
    public val body: List<Byte>,
    public val expires: Int,
    public val topic: String?,
    public val trace_id: String?,
  ) : TlGen_TonConnectRequest() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (topic != null) result = result or 1U
        if (trace_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(session_id)
      stream.writeInt32(msg_id)
      stream.writeByteArray(body.toByteArray())
      stream.writeInt32(expires)
      topic?.let { stream.writeString(it) }
      trace_id?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xA15F8513U
    }
  }
}
