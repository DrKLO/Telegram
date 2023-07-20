package org.telegram.ui.Stories;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.TLRPC;

public class MessageMediaStoryFull extends TLRPC.TL_messageMediaStory {

    public static int constructor = 0xc79aee1d;

    public void readParams(AbstractSerializedData stream, boolean exception) {
        user_id = stream.readInt64(exception);
        id = stream.readInt32(exception);
        storyItem = TLRPC.StoryItem.TLdeserialize(stream, stream.readInt32(exception), exception);
        via_mention = stream.readBool(exception);
    }

    public void serializeToStream(AbstractSerializedData stream) {
        stream.writeInt32(constructor);
        stream.writeInt64(user_id);
        stream.writeInt32(id);
        storyItem.serializeToStream(stream);
        stream.writeBool(via_mention);
    }
}