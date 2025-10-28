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

public sealed class TlGen_BotInfo : TlGen_Object {
  public data class TL_botInfo(
    public val has_preview_medias: Boolean,
    public val user_id: Long?,
    public val description: String?,
    public val description_photo: TlGen_Photo?,
    public val description_document: TlGen_Document?,
    public val commands: List<TlGen_BotCommand>?,
    public val menu_button: TlGen_BotMenuButton?,
    public val privacy_policy_url: String?,
    public val app_settings: TlGen_BotAppSettings?,
    public val verifier_settings: TlGen_BotVerifierSettings?,
  ) : TlGen_BotInfo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (user_id != null) result = result or 1U
        if (description != null) result = result or 2U
        if (commands != null) result = result or 4U
        if (menu_button != null) result = result or 8U
        if (description_photo != null) result = result or 16U
        if (description_document != null) result = result or 32U
        if (has_preview_medias) result = result or 64U
        if (privacy_policy_url != null) result = result or 128U
        if (app_settings != null) result = result or 256U
        if (verifier_settings != null) result = result or 512U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user_id?.let { stream.writeInt64(it) }
      description?.let { stream.writeString(it) }
      description_photo?.serializeToStream(stream)
      description_document?.serializeToStream(stream)
      commands?.let { TlGen_Vector.serialize(stream, it) }
      menu_button?.serializeToStream(stream)
      privacy_policy_url?.let { stream.writeString(it) }
      app_settings?.serializeToStream(stream)
      verifier_settings?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4D8A0299U
    }
  }

  public data object TL_botInfoEmpty_layer48 : TlGen_Object {
    public const val MAGIC: UInt = 0xBB2E37CEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_botInfo_layer48(
    public val user_id: Int,
    public val version: Int,
    public val share_text: String,
    public val description: String,
    public val commands: List<TlGen_BotCommand>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(user_id)
      stream.writeInt32(version)
      stream.writeString(share_text)
      stream.writeString(description)
      TlGen_Vector.serialize(stream, commands)
    }

    public companion object {
      public const val MAGIC: UInt = 0x09CF585DU
    }
  }

  public data class TL_botInfo_layer132(
    public val user_id: Int,
    public val description: String,
    public val commands: List<TlGen_BotCommand>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(user_id)
      stream.writeString(description)
      TlGen_Vector.serialize(stream, commands)
    }

    public companion object {
      public const val MAGIC: UInt = 0x98E81D3AU
    }
  }

  public data class TL_botInfo_layer139(
    public val user_id: Long,
    public val description: String,
    public val commands: List<TlGen_BotCommand>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeString(description)
      TlGen_Vector.serialize(stream, commands)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1B74B335U
    }
  }

  public data class TL_botInfo_layer142(
    public val user_id: Long,
    public val description: String,
    public val commands: List<TlGen_BotCommand>,
    public val menu_button: TlGen_BotMenuButton,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeString(description)
      TlGen_Vector.serialize(stream, commands)
      menu_button.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE4169B5DU
    }
  }

  public data class TL_botInfo_layer185(
    public val has_preview_medias: Boolean,
    public val user_id: Long?,
    public val description: String?,
    public val description_photo: TlGen_Photo?,
    public val description_document: TlGen_Document?,
    public val commands: List<TlGen_BotCommand>?,
    public val menu_button: TlGen_BotMenuButton?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (user_id != null) result = result or 1U
        if (description != null) result = result or 2U
        if (commands != null) result = result or 4U
        if (menu_button != null) result = result or 8U
        if (description_photo != null) result = result or 16U
        if (description_document != null) result = result or 32U
        if (has_preview_medias) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user_id?.let { stream.writeInt64(it) }
      description?.let { stream.writeString(it) }
      description_photo?.serializeToStream(stream)
      description_document?.serializeToStream(stream)
      commands?.let { TlGen_Vector.serialize(stream, it) }
      menu_button?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8F300B57U
    }
  }

  public data class TL_botInfo_layer192(
    public val has_preview_medias: Boolean,
    public val user_id: Long?,
    public val description: String?,
    public val description_photo: TlGen_Photo?,
    public val description_document: TlGen_Document?,
    public val commands: List<TlGen_BotCommand>?,
    public val menu_button: TlGen_BotMenuButton?,
    public val privacy_policy_url: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (user_id != null) result = result or 1U
        if (description != null) result = result or 2U
        if (commands != null) result = result or 4U
        if (menu_button != null) result = result or 8U
        if (description_photo != null) result = result or 16U
        if (description_document != null) result = result or 32U
        if (has_preview_medias) result = result or 64U
        if (privacy_policy_url != null) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user_id?.let { stream.writeInt64(it) }
      description?.let { stream.writeString(it) }
      description_photo?.serializeToStream(stream)
      description_document?.serializeToStream(stream)
      commands?.let { TlGen_Vector.serialize(stream, it) }
      menu_button?.serializeToStream(stream)
      privacy_policy_url?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x82437E74U
    }
  }

  public data class TL_botInfo_layer195(
    public val has_preview_medias: Boolean,
    public val user_id: Long?,
    public val description: String?,
    public val description_photo: TlGen_Photo?,
    public val description_document: TlGen_Document?,
    public val commands: List<TlGen_BotCommand>?,
    public val menu_button: TlGen_BotMenuButton?,
    public val privacy_policy_url: String?,
    public val app_settings: TlGen_BotAppSettings?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (user_id != null) result = result or 1U
        if (description != null) result = result or 2U
        if (commands != null) result = result or 4U
        if (menu_button != null) result = result or 8U
        if (description_photo != null) result = result or 16U
        if (description_document != null) result = result or 32U
        if (has_preview_medias) result = result or 64U
        if (privacy_policy_url != null) result = result or 128U
        if (app_settings != null) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user_id?.let { stream.writeInt64(it) }
      description?.let { stream.writeString(it) }
      description_photo?.serializeToStream(stream)
      description_document?.serializeToStream(stream)
      commands?.let { TlGen_Vector.serialize(stream, it) }
      menu_button?.serializeToStream(stream)
      privacy_policy_url?.let { stream.writeString(it) }
      app_settings?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x36607333U
    }
  }
}
