package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InlineButtonType : TlGen_Object {
  public data class TL_inlineButtonTypeUrl(
    public val url: String,
  ) : TlGen_InlineButtonType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xECA4F8D4U
    }
  }

  public data class TL_inlineButtonTypeUrlAuth(
    public val fwd_text: String?,
    public val url: String,
    public val button_id: Int,
  ) : TlGen_InlineButtonType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (fwd_text != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      fwd_text?.let { stream.writeString(it) }
      stream.writeString(url)
      stream.writeInt32(button_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBFD02DA2U
    }
  }

  public data class TL_inputInlineButtonTypeUrlAuth(
    public val request_write_access: Boolean,
    public val fwd_text: String?,
    public val url: String,
    public val bot: TlGen_InputUser?,
  ) : TlGen_InlineButtonType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (request_write_access) result = result or 1U
        if (fwd_text != null) result = result or 2U
        if (bot != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      fwd_text?.let { stream.writeString(it) }
      stream.writeString(url)
      bot?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9961BCB4U
    }
  }

  public data class TL_inlineButtonTypeWebView(
    public val url: String,
  ) : TlGen_InlineButtonType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3BCAB5B4U
    }
  }

  public data class TL_inlineButtonTypeCallback(
    public val requires_password: Boolean,
    public val `data`: List<Byte>,
  ) : TlGen_InlineButtonType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (requires_password) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeByteArray(data.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x2955BC38U
    }
  }

  public data object TL_inlineButtonTypeGame : TlGen_InlineButtonType() {
    public const val MAGIC: UInt = 0x5CD3709DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inlineButtonTypeBuy : TlGen_InlineButtonType() {
    public const val MAGIC: UInt = 0x48BAD7A5U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inlineButtonTypeSwitchInline(
    public val same_peer: Boolean,
    public val query: String,
    public val peer_types: List<TlGen_InlineQueryPeerType>?,
  ) : TlGen_InlineButtonType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (same_peer) result = result or 1U
        if (peer_types != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(query)
      peer_types?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x93773FF5U
    }
  }

  public data class TL_inlineButtonTypeUserProfile(
    public val user_id: Long,
  ) : TlGen_InlineButtonType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3FA33FCFU
    }
  }

  public data class TL_inputInlineButtonTypeUserProfile(
    public val user_id: TlGen_InputUser,
  ) : TlGen_InlineButtonType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      user_id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x53F3CE5AU
    }
  }

  public data class TL_inlineButtonTypeCopy(
    public val copy_text: String,
  ) : TlGen_InlineButtonType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(copy_text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB41D3272U
    }
  }

  public data object TL_inlineButtonTypeDisabled : TlGen_InlineButtonType() {
    public const val MAGIC: UInt = 0xA438619DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
