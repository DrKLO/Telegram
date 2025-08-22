package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BotInlineResult : TlGen_Object {
  public data class TL_botInlineMediaResult(
    public val id: String,
    public val type: String,
    public val photo: TlGen_Photo?,
    public val document: TlGen_Document?,
    public val title: String?,
    public val description: String?,
    public val send_message: TlGen_BotInlineMessage,
  ) : TlGen_BotInlineResult() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (photo != null) result = result or 1U
        if (document != null) result = result or 2U
        if (title != null) result = result or 4U
        if (description != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeString(type)
      photo?.serializeToStream(stream)
      document?.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      send_message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x17DB940BU
    }
  }

  public data class TL_botInlineResult(
    public val id: String,
    public val type: String,
    public val title: String?,
    public val description: String?,
    public val url: String?,
    public val thumb: TlGen_WebDocument?,
    public val content: TlGen_WebDocument?,
    public val send_message: TlGen_BotInlineMessage,
  ) : TlGen_BotInlineResult() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 2U
        if (description != null) result = result or 4U
        if (url != null) result = result or 8U
        if (thumb != null) result = result or 16U
        if (content != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeString(type)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      url?.let { stream.writeString(it) }
      thumb?.serializeToStream(stream)
      content?.serializeToStream(stream)
      send_message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x11965F3AU
    }
  }
}
