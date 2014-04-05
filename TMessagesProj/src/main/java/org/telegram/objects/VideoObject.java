package org.telegram.objects;

import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;

public class VideoObject extends ExtendedAttachmentObject<TLRPC.Video> {

    static {
        new AttachmentObjectWrapper.AttachmentObjectFactory<VideoObject>() {

            @Override
            public VideoObject create(TLObject rawObject) {
                if (rawObject instanceof TLRPC.Video) {
                    return new VideoObject((TLRPC.Video)rawObject);
                }
                throw new IllegalStateException();
            }
        };
    }

    public VideoObject(TLRPC.Video rawObject) {
        super(TLRPC.Video.class, rawObject);
    }

    @Override
    public TLRPC.InputFileLocation getAttachmentFileLocation() {
        return new TLRPC.TL_inputVideoFileLocation();
    }

    @Override
    public String getAttachmentFileExtension() {
        return ".mp4";
    }
}
