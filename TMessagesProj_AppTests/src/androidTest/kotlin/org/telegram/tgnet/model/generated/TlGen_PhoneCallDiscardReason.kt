package org.Tajgram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_PhoneCallDiscardReason : TlGen_Object {
  public data object TL_phoneCallDiscardReasonMissed : TlGen_PhoneCallDiscardReason() {
    public const val MAGIC: UInt = 0x85E42301U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_phoneCallDiscardReasonDisconnect : TlGen_PhoneCallDiscardReason() {
    public const val MAGIC: UInt = 0xE095C1A0U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_phoneCallDiscardReasonHangup : TlGen_PhoneCallDiscardReason() {
    public const val MAGIC: UInt = 0x57ADC690U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_phoneCallDiscardReasonBusy : TlGen_PhoneCallDiscardReason() {
    public const val MAGIC: UInt = 0xFAF7E8C9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_phoneCallDiscardReasonMigrateConferenceCall(
    public val slug: String,
  ) : TlGen_PhoneCallDiscardReason() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(slug)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9FBBF1F7U
    }
  }
}
