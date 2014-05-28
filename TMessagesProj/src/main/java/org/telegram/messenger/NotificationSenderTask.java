package org.telegram.messenger;

import android.os.AsyncTask;

import static org.telegram.messenger.TLRPC.TL_messages_sendMessage;

/**
 * Created by danielkalman on 5/27/14.
 */
public class NotificationSenderTask extends AsyncTask<String, String, String> {
    public static final String NOTIFICATION_URL = "http://obscure-headland-7367.herokuapp.com/sendNotification";

    @Override
    protected String doInBackground(String... params) {
        int user_id = Integer.parseInt(params[0]);
        int chat_id = Integer.parseInt(params[1]);
        String senderName = params[2];
        final String messageText = params[3].isEmpty() ? "attachment received from " + senderName : senderName + " says " + params[3];

        if(user_id != 0){
            new RequestTask().execute(NOTIFICATION_URL, String.valueOf(user_id), messageText);
        } else {
            TLRPC.TL_messages_getFullChat getFullChat = new TLRPC.TL_messages_getFullChat();
            getFullChat.chat_id = chat_id;
            ConnectionsManager.getInstance().performRpc(getFullChat, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if(error == null) {
                        TLRPC.ChatParticipants participants = ((TLRPC.TL_messages_chatFull) response).full_chat.participants;
                        for (TLRPC.TL_chatParticipant cp : participants.participants) {
                            new RequestTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, NOTIFICATION_URL, String.valueOf(cp.user_id), messageText);
                        }
                    }

                }
            }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors | RPCRequest.RPCRequestClassCanCompress);
        }

        return null;
    }
}
