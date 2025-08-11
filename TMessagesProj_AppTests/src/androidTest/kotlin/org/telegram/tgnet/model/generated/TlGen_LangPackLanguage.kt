package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_LangPackLanguage : TlGen_Object {
  public data class TL_langPackLanguage(
    public val official: Boolean,
    public val rtl: Boolean,
    public val beta: Boolean,
    public val name: String,
    public val native_name: String,
    public val lang_code: String,
    public val base_lang_code: String?,
    public val plural_code: String,
    public val strings_count: Int,
    public val translated_count: Int,
    public val translations_url: String,
  ) : TlGen_LangPackLanguage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (official) result = result or 1U
        if (base_lang_code != null) result = result or 2U
        if (rtl) result = result or 4U
        if (beta) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(name)
      stream.writeString(native_name)
      stream.writeString(lang_code)
      base_lang_code?.let { stream.writeString(it) }
      stream.writeString(plural_code)
      stream.writeInt32(strings_count)
      stream.writeInt32(translated_count)
      stream.writeString(translations_url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEECA5CE3U
    }
  }
}
