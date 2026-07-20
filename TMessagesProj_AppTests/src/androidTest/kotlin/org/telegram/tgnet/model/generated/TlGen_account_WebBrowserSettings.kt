package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_WebBrowserSettings : TlGen_Object {
  public data object TL_account_webBrowserSettingsNotModified : TlGen_account_WebBrowserSettings() {
    public const val MAGIC: UInt = 0xC31C8F4EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_account_webBrowserSettings(
    public val open_external_browser: Boolean,
    public val display_close_button: Boolean,
    public val external_exceptions: List<TlGen_WebDomainException>,
    public val inapp_exceptions: List<TlGen_WebDomainException>,
    public val hash: Long,
  ) : TlGen_account_WebBrowserSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (open_external_browser) result = result or 1U
        if (display_close_button) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, external_exceptions)
      TlGen_Vector.serialize(stream, inapp_exceptions)
      stream.writeInt64(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x79EB8CB3U
    }
  }
}
