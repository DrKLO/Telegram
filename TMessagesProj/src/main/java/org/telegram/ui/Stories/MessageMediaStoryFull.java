package org.Tajgram.ui.Stories;

import org.Tajgram.messenger.MessagesController;
import org.Tajgram.messenger.UserConfig;
import org.Tajgram.tgnet.AbstractSerializedData;
import org.Tajgram.tgnet.InputSerializedData;
import org.Tajgram.tgnet.OutputSerializedData;
import org.Tajgram.tgnet.TLRPC;
import org.Tajgram.tgnet.tl.TL_stories;

public class MessageMediaStoryFull extends TLRPC.TL_messageMediaStory {

    public static int constructor = 0xc79aee1d;

    public void readParams(InputSerializedData stream, boolean exception) {
        user_id = stream.readInt64(exception);
        id = stream.readInt32(exception);
        storyItem = TL_stories.StoryItem.TLdeserialize(stream, stream.readInt32(exception), exception);
        via_mention = stream.readBool(exception);
        peer = MessagesController.getInstance(UserConfig.selectedAccount).getPeer(user_id);
    }

    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
        stream.writeInt64(user_id);
        stream.writeInt32(id);
        storyItem.serializeToStream(stream);
        stream.writeBool(via_mention);
    }
}