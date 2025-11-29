package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PrivacyKey : TlGen_Object {
  public data object TL_privacyKeyStatusTimestamp : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0xBC2EAB30U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyChatInvite : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0x500E6DFAU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyPhoneCall : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0x3D662B7BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyPhoneP2P : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0x39491CC8U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyForwards : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0x69EC56A3U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyProfilePhoto : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0x96151FEDU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyPhoneNumber : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0xD19AE46DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyAddedByPhone : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0x42FFD42BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyVoiceMessages : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0x0697F414U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyAbout : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0xA486B761U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyBirthday : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0x2000A518U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyStarGiftsAutoSave : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0x2CA4FDF8U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeyNoPaidMessages : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0x17D348D2U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_privacyKeySavedMusic : TlGen_PrivacyKey() {
    public const val MAGIC: UInt = 0xFF7A571BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
