package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ReportReason : TlGen_Object {
  public data object TL_inputReportReasonSpam : TlGen_ReportReason() {
    public const val MAGIC: UInt = 0x58DBCAB8U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputReportReasonViolence : TlGen_ReportReason() {
    public const val MAGIC: UInt = 0x1E22C78DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputReportReasonPornography : TlGen_ReportReason() {
    public const val MAGIC: UInt = 0x2E59D922U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputReportReasonChildAbuse : TlGen_ReportReason() {
    public const val MAGIC: UInt = 0xADF44EE3U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputReportReasonCopyright : TlGen_ReportReason() {
    public const val MAGIC: UInt = 0x9B89F93AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputReportReasonGeoIrrelevant : TlGen_ReportReason() {
    public const val MAGIC: UInt = 0xDBD4FEEDU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputReportReasonFake : TlGen_ReportReason() {
    public const val MAGIC: UInt = 0xF5DDD6E7U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputReportReasonOther : TlGen_ReportReason() {
    public const val MAGIC: UInt = 0xC1E4A2B1U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputReportReasonIllegalDrugs : TlGen_ReportReason() {
    public const val MAGIC: UInt = 0x0A8EB2BEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputReportReasonPersonalDetails : TlGen_ReportReason() {
    public const val MAGIC: UInt = 0x9EC7863DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
