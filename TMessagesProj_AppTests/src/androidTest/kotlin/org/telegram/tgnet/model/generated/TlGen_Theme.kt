package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Theme : TlGen_Object {
  public data class TL_theme(
    public val creator: Boolean,
    public val default: Boolean,
    public val for_chat: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val slug: String,
    public val title: String,
    public val document: TlGen_Document?,
    public val settings: List<TlGen_ThemeSettings>?,
    public val emoticon: String?,
    public val installs_count: Int?,
  ) : TlGen_Theme() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (default) result = result or 2U
        if (document != null) result = result or 4U
        if (settings != null) result = result or 8U
        if (installs_count != null) result = result or 16U
        if (for_chat) result = result or 32U
        if (emoticon != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(slug)
      stream.writeString(title)
      document?.serializeToStream(stream)
      settings?.let { TlGen_Vector.serialize(stream, it) }
      emoticon?.let { stream.writeString(it) }
      installs_count?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xA00E67D6U
    }
  }

  public data object TL_themeDocumentNotModified_layer106 : TlGen_Object {
    public const val MAGIC: UInt = 0x483D270CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_theme_layer106(
    public val creator: Boolean,
    public val default: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val slug: String,
    public val title: String,
    public val document: TlGen_Document?,
    public val installs_count: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (default) result = result or 2U
        if (document != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(slug)
      stream.writeString(title)
      document?.serializeToStream(stream)
      stream.writeInt32(installs_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF7D90CE0U
    }
  }

  public data class TL_theme_layer131(
    public val creator: Boolean,
    public val default: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val slug: String,
    public val title: String,
    public val document: TlGen_Document?,
    public val settings: TlGen_ThemeSettings?,
    public val installs_count: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (default) result = result or 2U
        if (document != null) result = result or 4U
        if (settings != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(slug)
      stream.writeString(title)
      document?.serializeToStream(stream)
      settings?.serializeToStream(stream)
      stream.writeInt32(installs_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x028F1114U
    }
  }

  public data class TL_theme_layer133(
    public val creator: Boolean,
    public val default: Boolean,
    public val for_chat: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val slug: String,
    public val title: String,
    public val document: TlGen_Document?,
    public val settings: TlGen_ThemeSettings?,
    public val installs_count: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (default) result = result or 2U
        if (document != null) result = result or 4U
        if (settings != null) result = result or 8U
        if (installs_count != null) result = result or 16U
        if (for_chat) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(slug)
      stream.writeString(title)
      document?.serializeToStream(stream)
      settings?.serializeToStream(stream)
      installs_count?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xE802B8DCU
    }
  }
}
