package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WebDomainException : TlGen_Object {
  public data class TL_webDomainException(
    public val domain: String,
    public val url: String,
    public val title: String,
    public val favicon: Long?,
  ) : TlGen_WebDomainException() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (favicon != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(domain)
      stream.writeString(url)
      stream.writeString(title)
      favicon?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x933CA597U
    }
  }
}
