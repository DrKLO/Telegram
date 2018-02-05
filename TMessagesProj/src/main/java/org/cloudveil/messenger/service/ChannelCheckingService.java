package org.cloudveil.messenger.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.cloudveil.messenger.api.model.request.SettingsRequest;
import org.cloudveil.messenger.api.model.response.SettingsResponse;
import org.cloudveil.messenger.api.service.holder.ServiceClientHolders;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Dmitriy on 05.02.2018.
 */

public class ChannelCheckingService extends Service {
    private static final String ACTION_CHECK_CHANNELS = "org.cloudveil.messenger.service.check.channels";
    private Disposable subscription;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startDataChecking(@NonNull Context context) {
        Intent intent = new Intent(ACTION_CHECK_CHANNELS);
        intent.setClass(context, ChannelCheckingService.class);
        context.startService(intent);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() != null && intent.getAction().equals(ACTION_CHECK_CHANNELS) && subscription == null) {
            sendDataCheckRequest();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void sendDataCheckRequest() {
        SettingsRequest request = new SettingsRequest();
        addChatsToRequest(request);
        addUsersToRequest(request);

        request.userPhone = UserConfig.getCurrentUser().phone;
        request.userId = UserConfig.getCurrentUser().id;

        if(request.isEmpty()) {
            return;
        }
        subscription = ServiceClientHolders.getSettingsService().loadSettings(request).
                subscribeOn(Schedulers.io()).
                observeOn(AndroidSchedulers.mainThread()).
                subscribe(new Consumer<SettingsResponse>() {

                    @Override
                    public void accept(SettingsResponse settingsResponse) throws Exception {
                        freeSubscription();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        freeSubscription();
                    }
                });
    }

    private void freeSubscription() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
        subscription = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        freeSubscription();
    }

    private void addChatsToRequest(@NonNull SettingsRequest request) {
        ConcurrentHashMap<Integer, TLRPC.Chat> chats = MessagesController.getInstance().getChats();
        Collection<TLRPC.Chat> values = chats.values();


        for (TLRPC.Chat chat : values) {
            int id = chat.id;
            String title = chat.title;

            SettingsRequest.Row row = new SettingsRequest.Row();
            row.id = id;
            row.title = title;

            if (chat instanceof TLRPC.TL_channel) {
                request.channels.add(row);
            } else {
                request.groups.add(row);
            }
        }
    }

    private void addUsersToRequest(@NonNull SettingsRequest request) {
        ConcurrentHashMap<Integer, TLRPC.User> users = MessagesController.getInstance().getUsers();
        Collection<TLRPC.User> values = users.values();


        for (TLRPC.User user : values) {
            if (user.bot) {
                int id = user.id;
                String title = user.username;

                SettingsRequest.Row row = new SettingsRequest.Row();
                row.id = id;
                row.title = title;

                request.bots.add(row);
            }
        }
    }
}
