package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_AppUpdate : TlGen_Object {
  public data object TL_help_noAppUpdate : TlGen_help_AppUpdate() {
    public const val MAGIC: UInt = 0xC45A6536U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_help_appUpdate(
    public val can_not_skip: Boolean,
    public val id: Int,
    public val version: String,
    public val text: String,
    public val entities: List<TlGen_MessageEntity>,
    public val document: TlGen_Document?,
    public val url: String?,
    public val sticker: TlGen_Document?,
  ) : TlGen_help_AppUpdate() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (can_not_skip) result = result or 1U
        if (document != null) result = result or 2U
        if (url != null) result = result or 4U
        if (sticker != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(version)
      stream.writeString(text)
      TlGen_Vector.serialize(stream, entities)
      document?.serializeToStream(stream)
      url?.let { stream.writeString(it) }
      sticker?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCCBBCE30U
    }
  }
}
