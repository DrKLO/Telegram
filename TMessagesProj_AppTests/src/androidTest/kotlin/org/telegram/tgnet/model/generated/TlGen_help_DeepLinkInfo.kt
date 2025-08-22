package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_DeepLinkInfo : TlGen_Object {
  public data object TL_help_deepLinkInfoEmpty : TlGen_help_DeepLinkInfo() {
    public const val MAGIC: UInt = 0x66AFA166U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_help_deepLinkInfo(
    public val update_app: Boolean,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
  ) : TlGen_help_DeepLinkInfo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (update_app) result = result or 1U
        if (entities != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x6A4EE832U
    }
  }
}
