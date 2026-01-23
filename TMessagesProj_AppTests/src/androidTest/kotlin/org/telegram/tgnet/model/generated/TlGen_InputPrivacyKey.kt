package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputPrivacyKey : TlGen_Object {
  public data object TL_inputPrivacyKeyStatusTimestamp : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0x4F96CB18U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyChatInvite : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0xBDFB0426U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyPhoneCall : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0xFABADC5FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyPhoneP2P : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0xDB9E70D2U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyForwards : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0xA4DD4C08U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyProfilePhoto : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0x5719BACCU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyPhoneNumber : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0x0352DAFAU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyAddedByPhone : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0xD1219BDDU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyVoiceMessages : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0xAEE69D68U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyAbout : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0x3823CC40U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyBirthday : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0xD65A11CCU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyStarGiftsAutoSave : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0xE1732341U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeyNoPaidMessages : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0xBDC597B4U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPrivacyKeySavedMusic : TlGen_InputPrivacyKey() {
    public const val MAGIC: UInt = 0x4DBE9226U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
