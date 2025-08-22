package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessagesFilter : TlGen_Object {
  public data object TL_inputMessagesFilterEmpty : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0x57E2F66CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterPhotos : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0x9609A51CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterVideo : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0x9FC00E65U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterPhotoVideo : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0x56E9F0E4U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterDocument : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0x9EDDF188U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterUrl : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0x7EF0DD87U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterGif : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0xFFC86587U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterVoice : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0x50F5C392U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterMusic : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0x3751B49EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterChatPhotos : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0x3A20ECB8U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputMessagesFilterPhoneCalls(
    public val missed: Boolean,
  ) : TlGen_MessagesFilter() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (missed) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x80C99768U
    }
  }

  public data object TL_inputMessagesFilterRoundVoice : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0x7A7C17A4U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterRoundVideo : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0xB549DA53U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterMyMentions : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0xC1F8E69AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterGeo : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0xE7026D0DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterContacts : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0xE062DB83U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputMessagesFilterPinned : TlGen_MessagesFilter() {
    public const val MAGIC: UInt = 0x1BB00451U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
