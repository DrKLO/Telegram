package org.telegram.ui.Stories;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;

public class StoryCustomParamsHelper {

    public static boolean isEmpty(TL_stories.StoryItem storyItem) {
        return storyItem.detectedLng == null && storyItem.translatedLng == null && !storyItem.translated && storyItem.translatedText == null;
    }

    public static void copyParams(TL_stories.StoryItem fromStory, TL_stories.StoryItem toStory) {
        toStory.translated = fromStory.translated;
        toStory.detectedLng = fromStory.detectedLng;
        toStory.translatedText = fromStory.translatedText;
        toStory.translatedLng = fromStory.translatedLng;
    }

    public static void readLocalParams(TL_stories.StoryItem storyItem, NativeByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return;
        }
        int version = byteBuffer.readInt32(true);
        TLObject params;
        switch (version) {
            case 1:
                params = new Params_v1(storyItem);
                break;
            default:
                throw new RuntimeException("(story) can't read params version = " + version);
        }
        params.readParams(byteBuffer, true);
    }

    public static NativeByteBuffer writeLocalParams(TL_stories.StoryItem storyItem) {
        if (isEmpty(storyItem)) {
            return null;
        }
        TLObject params = new Params_v1(storyItem);
        try {
            NativeByteBuffer nativeByteBuffer = new NativeByteBuffer(params.getObjectSize());
            params.serializeToStream(nativeByteBuffer);
            return nativeByteBuffer;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static class Params_v1 extends TLObject {

        private final static int VERSION = 1;
        final TL_stories.StoryItem storyItem;
        int flags = 0;

        private Params_v1(TL_stories.StoryItem storyItem) {
            this.storyItem = storyItem;
            flags += storyItem.translated ? 1 : 0;
            flags += storyItem.detectedLng != null ? 2 : 0;
            flags += storyItem.translatedText != null ? 4 : 0;
            flags += storyItem.translatedLng != null ? 8 : 0;
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(VERSION);
            stream.writeInt32(flags);
            if ((flags & 2) != 0) {
                stream.writeString(storyItem.detectedLng);
            }
            if ((flags & 4) != 0) {
                storyItem.translatedText.serializeToStream(stream);
            }
            if ((flags & 8) != 0) {
                stream.writeString(storyItem.translatedLng);
            }
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(true);
            storyItem.translated = (flags & 1) != 0;
            if ((flags & 2) != 0) {
                storyItem.detectedLng = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                storyItem.translatedText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 8) != 0) {
                storyItem.translatedLng = stream.readString(exception);
            }
        }
    }
}
