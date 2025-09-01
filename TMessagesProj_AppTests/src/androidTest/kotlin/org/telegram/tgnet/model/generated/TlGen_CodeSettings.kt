package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_CodeSettings : TlGen_Object {
  public data class TL_codeSettings(
    public val allow_flashcall: Boolean,
    public val current_number: Boolean,
    public val allow_app_hash: Boolean,
    public val allow_missed_call: Boolean,
    public val allow_firebase: Boolean,
    public val unknown_number: Boolean,
    public val logout_tokens: List<List<Byte>>?,
    public val multiflags_8: Multiflags_8?,
  ) : TlGen_CodeSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (allow_flashcall) result = result or 1U
        if (current_number) result = result or 2U
        if (allow_app_hash) result = result or 16U
        if (allow_missed_call) result = result or 32U
        if (logout_tokens != null) result = result or 64U
        if (allow_firebase) result = result or 128U
        if (multiflags_8 != null) result = result or 256U
        if (unknown_number) result = result or 512U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      logout_tokens?.let { TlGen_Vector.serializeBytes(stream, it) }
      multiflags_8?.let { stream.writeString(it.token) }
      multiflags_8?.let { stream.writeBool(it.app_sandbox) }
    }

    public data class Multiflags_8(
      public val token: String,
      public val app_sandbox: Boolean,
    )

    public companion object {
      public const val MAGIC: UInt = 0xAD253D78U
    }
  }
}
