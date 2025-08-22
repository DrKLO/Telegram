package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_TopPeerCategory : TlGen_Object {
  public data object TL_topPeerCategoryBotsPM : TlGen_TopPeerCategory() {
    public const val MAGIC: UInt = 0xAB661B5BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_topPeerCategoryBotsInline : TlGen_TopPeerCategory() {
    public const val MAGIC: UInt = 0x148677E2U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_topPeerCategoryCorrespondents : TlGen_TopPeerCategory() {
    public const val MAGIC: UInt = 0x0637B7EDU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_topPeerCategoryGroups : TlGen_TopPeerCategory() {
    public const val MAGIC: UInt = 0xBD17A14AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_topPeerCategoryChannels : TlGen_TopPeerCategory() {
    public const val MAGIC: UInt = 0x161D9628U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_topPeerCategoryPhoneCalls : TlGen_TopPeerCategory() {
    public const val MAGIC: UInt = 0x1E76A78CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_topPeerCategoryForwardUsers : TlGen_TopPeerCategory() {
    public const val MAGIC: UInt = 0xA8406CA9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_topPeerCategoryForwardChats : TlGen_TopPeerCategory() {
    public const val MAGIC: UInt = 0xFBEEC0F0U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_topPeerCategoryBotsApp : TlGen_TopPeerCategory() {
    public const val MAGIC: UInt = 0xFD9E7BECU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
