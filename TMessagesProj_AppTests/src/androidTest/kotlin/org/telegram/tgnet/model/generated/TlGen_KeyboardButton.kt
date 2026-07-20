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

public sealed class TlGen_KeyboardButton : TlGen_Object {
  public data class TL_keyboardButton(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7D170CFFU
    }
  }

  public data class TL_keyboardButtonUrl(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val url: String,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD80C25ECU
    }
  }

  public data class TL_keyboardButtonCallback(
    public val requires_password: Boolean,
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val `data`: List<Byte>,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (requires_password) result = result or 1U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      stream.writeByteArray(data.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xE62BC960U
    }
  }

  public data class TL_keyboardButtonRequestPhone(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x417EFD8FU
    }
  }

  public data class TL_keyboardButtonRequestGeoLocation(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAA40F94DU
    }
  }

  public data class TL_keyboardButtonSwitchInline(
    public val same_peer: Boolean,
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val query: String,
    public val peer_types: List<TlGen_InlineQueryPeerType>?,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (same_peer) result = result or 1U
        if (peer_types != null) result = result or 2U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      stream.writeString(query)
      peer_types?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x991399FCU
    }
  }

  public data class TL_keyboardButtonGame(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x89C590F9U
    }
  }

  public data class TL_keyboardButtonBuy(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3FA53905U
    }
  }

  public data class TL_keyboardButtonUrlAuth(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val fwd_text: String?,
    public val url: String,
    public val button_id: Int,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (fwd_text != null) result = result or 1U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      fwd_text?.let { stream.writeString(it) }
      stream.writeString(url)
      stream.writeInt32(button_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF51006F9U
    }
  }

  public data class TL_inputKeyboardButtonUrlAuth(
    public val request_write_access: Boolean,
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val fwd_text: String?,
    public val url: String,
    public val bot: TlGen_InputUser,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (request_write_access) result = result or 1U
        if (fwd_text != null) result = result or 2U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      fwd_text?.let { stream.writeString(it) }
      stream.writeString(url)
      bot.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x68013E72U
    }
  }

  public data class TL_keyboardButtonRequestPoll(
    public val style: TlGen_KeyboardButtonStyle?,
    public val quiz: Boolean?,
    public val text: String,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (quiz != null) result = result or 1U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      quiz?.let { stream.writeBool(it) }
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7A11D782U
    }
  }

  public data class TL_inputKeyboardButtonUserProfile(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val user_id: TlGen_InputUser,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      user_id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7D5E07C7U
    }
  }

  public data class TL_keyboardButtonUserProfile(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val user_id: Long,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC0FD5D09U
    }
  }

  public data class TL_keyboardButtonWebView(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val url: String,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE846B1A0U
    }
  }

  public data class TL_keyboardButtonSimpleWebView(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val url: String,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE15C4370U
    }
  }

  public data class TL_keyboardButtonRequestPeer(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val button_id: Int,
    public val peer_type: TlGen_RequestPeerType,
    public val max_quantity: Int,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      stream.writeInt32(button_id)
      peer_type.serializeToStream(stream)
      stream.writeInt32(max_quantity)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5B0F15F5U
    }
  }

  public data class TL_keyboardButtonCopy(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val copy_text: String,
  ) : TlGen_KeyboardButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      stream.writeString(copy_text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBCC4AF10U
    }
  }

  public data class TL_keyboardButton_layer221(
    public val text: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA2FA4880U
    }
  }

  public data class TL_keyboardButtonUrl_layer221(
    public val text: String,
    public val url: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0x258AFF05U
    }
  }

  public data class TL_keyboardButtonCallback_layer117(
    public val text: String,
    public val `data`: List<Byte>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeByteArray(data.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x683A5E46U
    }
  }

  public data class TL_keyboardButtonRequestPhone_layer221(
    public val text: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB16A6C29U
    }
  }

  public data class TL_keyboardButtonRequestGeoLocation_layer221(
    public val text: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFC796B3FU
    }
  }

  public data class TL_keyboardButtonSwitchInline_layer54(
    public val text: String,
    public val query: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeString(query)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEA1B7A14U
    }
  }

  public data class TL_keyboardButtonSwitchInline_layer157(
    public val same_peer: Boolean,
    public val text: String,
    public val query: String,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (same_peer) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(text)
      stream.writeString(query)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0568A748U
    }
  }

  public data class TL_keyboardButtonGame_layer221(
    public val text: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x50F41CCFU
    }
  }

  public data class TL_keyboardButtonBuy_layer221(
    public val text: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAFD93FBBU
    }
  }

  public data class TL_keyboardButtonUrlAuth_layer221(
    public val text: String,
    public val fwd_text: String?,
    public val url: String,
    public val button_id: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (fwd_text != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(text)
      fwd_text?.let { stream.writeString(it) }
      stream.writeString(url)
      stream.writeInt32(button_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x10B78D29U
    }
  }

  public data class TL_inputKeyboardButtonUrlAuth_layer221(
    public val request_write_access: Boolean,
    public val text: String,
    public val fwd_text: String?,
    public val url: String,
    public val bot: TlGen_InputUser,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (request_write_access) result = result or 1U
        if (fwd_text != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(text)
      fwd_text?.let { stream.writeString(it) }
      stream.writeString(url)
      bot.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD02E7FD4U
    }
  }

  public data class TL_keyboardButtonRequestPoll_layer221(
    public val quiz: Boolean?,
    public val text: String,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (quiz != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      quiz?.let { stream.writeBool(it) }
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBBC7515DU
    }
  }

  public data class TL_keyboardButtonCallback_layer221(
    public val requires_password: Boolean,
    public val text: String,
    public val `data`: List<Byte>,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (requires_password) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(text)
      stream.writeByteArray(data.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x35BBDB6BU
    }
  }

  public data class TL_inputKeyboardButtonUserProfile_layer221(
    public val text: String,
    public val user_id: TlGen_InputUser,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      user_id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE988037BU
    }
  }

  public data class TL_keyboardButtonUserProfile_layer221(
    public val text: String,
    public val user_id: Long,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x308660C1U
    }
  }

  public data class TL_keyboardButtonWebView_layer221(
    public val text: String,
    public val url: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0x13767230U
    }
  }

  public data class TL_keyboardButtonSimpleWebView_layer221(
    public val text: String,
    public val url: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA0C0505CU
    }
  }

  public data class TL_keyboardButtonRequestPeer_layer168(
    public val text: String,
    public val button_id: Int,
    public val peer_type: TlGen_RequestPeerType,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeInt32(button_id)
      peer_type.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0D0B468CU
    }
  }

  public data class TL_keyboardButtonSwitchInline_layer221(
    public val same_peer: Boolean,
    public val text: String,
    public val query: String,
    public val peer_types: List<TlGen_InlineQueryPeerType>?,
  ) : TlGen_Object {
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
      stream.writeString(text)
      stream.writeString(query)
      peer_types?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x93B9FBB5U
    }
  }

  public data class TL_keyboardButtonRequestPeer_layer221(
    public val text: String,
    public val button_id: Int,
    public val peer_type: TlGen_RequestPeerType,
    public val max_quantity: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeInt32(button_id)
      peer_type.serializeToStream(stream)
      stream.writeInt32(max_quantity)
    }

    public companion object {
      public const val MAGIC: UInt = 0x53D7BFD8U
    }
  }

  public data class TL_keyboardButtonCopy_layer221(
    public val text: String,
    public val copy_text: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeString(copy_text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x75D2698EU
    }
  }
}
