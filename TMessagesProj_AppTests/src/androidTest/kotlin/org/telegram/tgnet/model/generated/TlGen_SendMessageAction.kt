package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SendMessageAction : TlGen_Object {
  public data object TL_sendMessageTypingAction : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0x16BF744EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_sendMessageCancelAction : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0xFD5EC8F5U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_sendMessageRecordVideoAction : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0xA187D66FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_sendMessageRecordAudioAction : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0xD52F73F7U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_sendMessageGeoLocationAction : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0x176F8BA1U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_sendMessageChooseContactAction : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0x628CBC6FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_sendMessageUploadVideoAction(
    public val progress: Int,
  ) : TlGen_SendMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(progress)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE9763AECU
    }
  }

  public data class TL_sendMessageUploadAudioAction(
    public val progress: Int,
  ) : TlGen_SendMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(progress)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF351D7ABU
    }
  }

  public data class TL_sendMessageUploadPhotoAction(
    public val progress: Int,
  ) : TlGen_SendMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(progress)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD1D34A26U
    }
  }

  public data class TL_sendMessageUploadDocumentAction(
    public val progress: Int,
  ) : TlGen_SendMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(progress)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAA0CD9E4U
    }
  }

  public data object TL_sendMessageGamePlayAction : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0xDD6A8F48U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_sendMessageRecordRoundAction : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0x88F27FBCU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_sendMessageUploadRoundAction(
    public val progress: Int,
  ) : TlGen_SendMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(progress)
    }

    public companion object {
      public const val MAGIC: UInt = 0x243E1C66U
    }
  }

  public data object TL_speakingInGroupCallAction : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0xD92C2285U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_sendMessageHistoryImportAction(
    public val progress: Int,
  ) : TlGen_SendMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(progress)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDBDA9246U
    }
  }

  public data object TL_sendMessageChooseStickerAction : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0xB05AC6B1U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_sendMessageEmojiInteraction(
    public val emoticon: String,
    public val msg_id: Int,
    public val interaction: TlGen_DataJSON,
  ) : TlGen_SendMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(emoticon)
      stream.writeInt32(msg_id)
      interaction.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x25972BCBU
    }
  }

  public data class TL_sendMessageEmojiInteractionSeen(
    public val emoticon: String,
  ) : TlGen_SendMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(emoticon)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB665902EU
    }
  }

  public data class TL_sendMessageTextDraftAction(
    public val random_id: Long,
    public val text: TlGen_TextWithEntities,
  ) : TlGen_SendMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(random_id)
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x376D975CU
    }
  }

  public data object TL_sendMessageUploadVideoAction_layer17 : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0x92042FF7U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_sendMessageUploadAudioAction_layer17 : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0xE6AC8A6FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_sendMessageUploadPhotoAction_layer17 : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0x990A3C1AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_sendMessageUploadDocumentAction_layer17 : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0x8FAEE98EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_sendMessageUploadRoundAction_layer66 : TlGen_SendMessageAction() {
    public const val MAGIC: UInt = 0xBB718624U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
