package org.telegram.objects;

import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;

public class DocumentObject extends ExtendedAttachmentObject<TLRPC.Document> {

    static {
        new AttachmentObjectWrapper.AttachmentObjectFactory<DocumentObject>() {

            @Override
            public DocumentObject create(TLObject rawObject) {
                if (rawObject instanceof TLRPC.Document) {
                    return new DocumentObject((TLRPC.Document)rawObject);
                }
                throw new IllegalStateException();
            }
        };
    }

    public DocumentObject(TLRPC.Document rawObject) {
        super(TLRPC.Document.class, rawObject);
    }

    @Override
    public TLRPC.InputFileLocation getAttachmentFileLocation() {
        return new TLRPC.TL_inputDocumentFileLocation();
    }

    @Override
    public String getAttachmentFileExtension() {
        String ext = rawObject.file_name;
        int idx;
        if (ext == null || (idx = ext.lastIndexOf(".")) == -1) {
            return "";
        }
        return ext.substring(idx);
    }
}
