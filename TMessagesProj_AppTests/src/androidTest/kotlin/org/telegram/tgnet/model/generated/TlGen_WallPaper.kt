package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WallPaper : TlGen_Object {
  public data class TL_wallPaper(
    public val id: Long,
    public val creator: Boolean,
    public val default: Boolean,
    public val pattern: Boolean,
    public val dark: Boolean,
    public val access_hash: Long,
    public val slug: String,
    public val document: TlGen_Document,
    public val settings: TlGen_WallPaperSettings?,
  ) : TlGen_WallPaper() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (default) result = result or 2U
        if (settings != null) result = result or 4U
        if (pattern) result = result or 8U
        if (dark) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(flags.toInt())
      stream.writeInt64(access_hash)
      stream.writeString(slug)
      document.serializeToStream(stream)
      settings?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA437C3EDU
    }
  }

  public data class TL_wallPaperNoFile(
    public val id: Long,
    public val default: Boolean,
    public val dark: Boolean,
    public val settings: TlGen_WallPaperSettings?,
  ) : TlGen_WallPaper() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (default) result = result or 2U
        if (settings != null) result = result or 4U
        if (dark) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(flags.toInt())
      settings?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE0804116U
    }
  }

  public data class TL_wallPaperNoFile_layer128(
    public val default: Boolean,
    public val dark: Boolean,
    public val settings: TlGen_WallPaperSettings?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (default) result = result or 2U
        if (settings != null) result = result or 4U
        if (dark) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      settings?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8AF40B25U
    }
  }
}
