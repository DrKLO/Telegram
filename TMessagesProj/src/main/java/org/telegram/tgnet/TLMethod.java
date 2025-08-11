package org.telegram.tgnet;

public abstract class TLMethod<T extends TLObject> extends TLObject {

    @Override
    final public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
        return deserializeResponseT(stream, constructor, exception);
    }

    abstract public T deserializeResponseT(InputSerializedData stream, int constructor, boolean exception);
}
