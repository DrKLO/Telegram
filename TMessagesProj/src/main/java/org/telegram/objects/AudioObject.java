package org.telegram.objects;

import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;

public class AudioObject extends ExtendedAttachmentObject<TLRPC.Audio> {

    static {
        new AttachmentObjectWrapper.AttachmentObjectFactory<AudioObject>() {

            @Override
            public AudioObject create(TLObject rawObject) {
                if (rawObject instanceof TLRPC.Audio) {
                    return new AudioObject((TLRPC.Audio)rawObject);
                }
                throw new IllegalStateException();
            }
        };
    }

    protected AudioObject(TLRPC.Audio rawObject) {
        super(TLRPC.Audio.class, rawObject);
    }

    @Override
    public TLRPC.InputFileLocation getAttachmentFileLocation() {
        return new TLRPC.TL_inputAudioFileLocation();
    }

    @Override
    public String getAttachmentFileExtension() {
        return ".m4a";
    }
}
