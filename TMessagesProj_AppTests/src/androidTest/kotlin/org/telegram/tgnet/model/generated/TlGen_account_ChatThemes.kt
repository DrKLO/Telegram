package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_ChatThemes : TlGen_Object {
  public data object TL_account_chatThemesNotModified : TlGen_account_ChatThemes() {
    public const val MAGIC: UInt = 0xE011E1C4U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_account_chatThemes(
    public val hash: Long,
    public val themes: List<TlGen_ChatTheme>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
    public val next_offset: String?,
  ) : TlGen_account_ChatThemes() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_offset != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, themes)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
      next_offset?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xBE098173U
    }
  }
}
