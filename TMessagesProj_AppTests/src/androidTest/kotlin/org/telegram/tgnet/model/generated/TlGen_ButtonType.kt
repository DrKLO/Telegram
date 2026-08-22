package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ButtonType : TlGen_Object {
  public data object TL_buttonTypeDefault : TlGen_ButtonType() {
    public const val MAGIC: UInt = 0xC9DD90E9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_buttonTypeRequestPhone : TlGen_ButtonType() {
    public const val MAGIC: UInt = 0xDF3D36F9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_buttonTypeRequestGeoLocation : TlGen_ButtonType() {
    public const val MAGIC: UInt = 0x9BEEE140U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_buttonTypeRequestPoll(
    public val quiz: Boolean?,
  ) : TlGen_ButtonType() {
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
    }

    public companion object {
      public const val MAGIC: UInt = 0xAACFFF84U
    }
  }

  public data class TL_buttonTypeRequestPeer(
    public val button_id: Int,
    public val peer_type: TlGen_RequestPeerType,
    public val max_quantity: Int,
  ) : TlGen_ButtonType() {
    internal val flags: UInt
      get() {
        var result = 0U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(button_id)
      peer_type.serializeToStream(stream)
      stream.writeInt32(max_quantity)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4F58A237U
    }
  }

  public data class TL_inputButtonTypeRequestPeer(
    public val name_requested: Boolean,
    public val username_requested: Boolean,
    public val photo_requested: Boolean,
    public val button_id: Int,
    public val peer_type: TlGen_RequestPeerType,
    public val max_quantity: Int,
  ) : TlGen_ButtonType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (name_requested) result = result or 1U
        if (username_requested) result = result or 2U
        if (photo_requested) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(button_id)
      peer_type.serializeToStream(stream)
      stream.writeInt32(max_quantity)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3FE268FEU
    }
  }

  public data class TL_buttonTypeSimpleWebView(
    public val url: String,
  ) : TlGen_ButtonType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC01A597AU
    }
  }
}
