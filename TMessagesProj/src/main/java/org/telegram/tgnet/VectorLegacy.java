package org.telegram.tgnet;

import java.util.ArrayList;

public class VectorLegacy {
    private VectorLegacy() {

    }

    public static ArrayList<Long> deserialize_IntAsLong(InputSerializedData stream, boolean exception) {
        ArrayList<Integer> res = Vector.deserializeInt(stream, exception);
        ArrayList<Long> result = new ArrayList<>(res.size());
        for (int num : res) {
            result.add((long) num);
        }
        return result;
    }

    public static void serialize_LongAsInt(OutputSerializedData stream, ArrayList<Long> objects) {
        ArrayList<Integer> ser = new ArrayList<>(objects.size());
        for (long num : objects) {
            ser.add((int) num);
        }
        Vector.serializeInt(stream, ser);
    }

    public static ArrayList<TLRPC.Peer> deserialize_IntUserIdAsPeer(InputSerializedData stream, boolean exception) {
        ArrayList<Integer> userIds = Vector.deserializeInt(stream, exception);
        final ArrayList<TLRPC.Peer> result = new ArrayList<>(userIds.size());
        for (int userId: userIds) {
            TLRPC.TL_peerUser user = new TLRPC.TL_peerUser();
            user.user_id = userId;
            result.add(user);
        }

        return result;
    }

    public static void serialize_PeerAsIntUserId(OutputSerializedData stream, ArrayList<TLRPC.Peer> objects) {
        stream.writeInt32(Vector.constructor);
        final int count = objects.size();
        stream.writeInt32(count);
        for (int a = 0; a < count; a++) {
            stream.writeInt32((int) objects.get(a).user_id);
        }
    }

    public static ArrayList<TLRPC.Peer> deserialize_LongUserIdAsPeer(InputSerializedData stream, boolean exception) {
        ArrayList<Long> userIds = Vector.deserializeLong(stream, exception);
        final ArrayList<TLRPC.Peer> result = new ArrayList<>(userIds.size());
        for (long userId: userIds) {
            TLRPC.TL_peerUser user = new TLRPC.TL_peerUser();
            user.user_id = userId;
            result.add(user);
        }

        return result;
    }

    public static void serialize_PeerAsLongUserId(OutputSerializedData stream, ArrayList<TLRPC.Peer> objects) {
        stream.writeInt32(Vector.constructor);
        final int count = objects.size();
        stream.writeInt32(count);
        for (int a = 0; a < count; a++) {
            stream.writeInt64(objects.get(a).user_id);
        }
    }
}
