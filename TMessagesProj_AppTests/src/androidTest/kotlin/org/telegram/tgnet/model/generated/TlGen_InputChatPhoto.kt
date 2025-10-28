package org.telegram.tgnet.model.generated

import kotlin.Double
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputChatPhoto : TlGen_Object {
  public data object TL_inputChatPhotoEmpty : TlGen_InputChatPhoto() {
    public const val MAGIC: UInt = 0x1CA48F57U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputChatPhoto(
    public val id: TlGen_InputPhoto,
  ) : TlGen_InputChatPhoto() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8953AD37U
    }
  }

  public data class TL_inputChatUploadedPhoto(
    public val `file`: TlGen_InputFile?,
    public val video: TlGen_InputFile?,
    public val video_start_ts: Double?,
    public val video_emoji_markup: TlGen_VideoSize?,
  ) : TlGen_InputChatPhoto() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (file != null) result = result or 1U
        if (video != null) result = result or 2U
        if (video_start_ts != null) result = result or 4U
        if (video_emoji_markup != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      file?.serializeToStream(stream)
      video?.serializeToStream(stream)
      video_start_ts?.let { stream.writeDouble(it) }
      video_emoji_markup?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBDCDAEC0U
    }
  }
}
