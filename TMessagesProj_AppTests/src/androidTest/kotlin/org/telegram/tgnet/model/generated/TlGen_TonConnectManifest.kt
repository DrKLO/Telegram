package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_TonConnectManifest : TlGen_Object {
  public data class TL_tonConnectManifest(
    public val url: String,
    public val name: String,
    public val icon: TlGen_WebDocument?,
  ) : TlGen_TonConnectManifest() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (icon != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(url)
      stream.writeString(name)
      icon?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x12229264U
    }
  }
}
