package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.PreviewView;
import org.telegram.ui.web.HttpGetFileTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class BotShareSheet extends BottomSheetWithRecyclerListView {

    private final int currentAccount;
    private final long botId;
    private final String botName;
    private final TLRPC.TL_messages_preparedInlineMessage message;

    private UniversalAdapter adapter;

    private final ChatMessageCell messageCell;
    private final ChatActionCell actionCell;
    private MessageObject messageObject;
    private final LinearLayout chatListView;
    private final SizeNotifierFrameLayout chatView;

    private final FrameLayout buttonContainer;
    private final ButtonWithCounterView button;

    public static void share(Context context, int currentAccount, long botId, String id, Theme.ResourcesProvider resourcesProvider, Runnable whenOpened, Utilities.Callback2<String, ArrayList<Long>> whenDone) {
        final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.showDelayed(500);

        final TLRPC.TL_messages_getPreparedInlineMessage req = new TLRPC.TL_messages_getPreparedInlineMessage();
        req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
        req.id = id;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.TL_messages_preparedInlineMessage) {
                final TLRPC.TL_messages_preparedInlineMessage result = (TLRPC.TL_messages_preparedInlineMessage) res;
                if (result.result.send_message instanceof TLRPC.TL_botInlineMessageMediaWebPage) {
                    final TLRPC.TL_botInlineMessageMediaWebPage m = (TLRPC.TL_botInlineMessageMediaWebPage) result.result.send_message;
                    if (!TextUtils.isEmpty(m.url)) {
                        final Runnable callback = loadWebPagePreview(currentAccount, m.url, webpage -> {
                            progressDialog.dismiss();
                            new BotShareSheet(context, currentAccount, botId, result, null, webpage, resourcesProvider, whenOpened, whenDone).show();
                        });
                        progressDialog.setOnCancelListener(di -> callback.run());
                        return;
                    }
                }
                final File[] finalFile = new File[1];
                final Runnable open = () -> {
                    progressDialog.dismiss();
                    new BotShareSheet(context, currentAccount, botId, result, finalFile[0], null, resourcesProvider, whenOpened, whenDone).show();
                };
                if (result != null && result.result.content != null && !TextUtils.isEmpty(result.result.content.url) && (
                    result.result.send_message instanceof TLRPC.TL_botInlineMessageMediaAuto ||
                    result.result.send_message instanceof TLRPC.TL_botInlineMessageMediaWebPage
                )) {
                    final String url = result.result.content.url;
                    String ext = ImageLoader.getHttpUrlExtension(url, null);
                    if (TextUtils.isEmpty(ext)) {
                        ext = FileLoader.getExtensionByMimeType(result.result.content.mime_type);
                    } else {
                        ext = "." + ext;
                    }
                    final File file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(url) + ext);
                    if (!file.exists()) {
                        HttpGetFileTask fileLoader = new HttpGetFileTask(f -> {
                            finalFile[0] = f;
                            open.run();
                        }, null);
                        fileLoader.setDestFile(file);
                        fileLoader.setMaxSize(8 * 1024 * 1024);
                        fileLoader.execute(url);
                        progressDialog.setOnCancelListener(v -> {
                            fileLoader.cancel(true);
                        });
                    } else {
                        open.run();
                    }
                } else {
                    open.run();
                }
            } else {
                if (whenDone != null) {
                    whenDone.run("MESSAGE_EXPIRED", null);
                }
            }
        }));
    }

    public static Runnable loadWebPagePreview(int currentAccount, String url, Utilities.Callback<TLRPC.WebPage> whenLoaded) {
        final int[] reqId = new int[1];
        final NotificationCenter.NotificationCenterDelegate[] delegateToRemove = new NotificationCenter.NotificationCenterDelegate[1];

        final TL_account.getWebPagePreview req = new TL_account.getWebPagePreview();
        req.message = url;
        reqId[0] = ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            reqId[0] = -1;
            if (res.media instanceof TLRPC.TL_messageMediaEmpty || res.media.webpage instanceof TLRPC.TL_webPageEmpty) {
                whenLoaded.run(null);
                return;
            }
            if (res.media instanceof TLRPC.TL_messageMediaWebPage) {
                final TLRPC.WebPage webPage = res.media.webpage;
                if (webPage instanceof TLRPC.TL_webPagePending) {
                    final long pendingId = webPage.id;
                    final NotificationCenter.NotificationCenterDelegate delegate = new NotificationCenter.NotificationCenterDelegate() {
                        @Override
                        public void didReceivedNotification(int id, int account, Object... args) {
                            if (id == NotificationCenter.didReceivedWebpagesInUpdates) {
                                final LongSparseArray<TLRPC.WebPage> webPages = (LongSparseArray<TLRPC.WebPage>) args[0];
                                if (webPages != null && webPages.containsKey(pendingId)) {
                                    final TLRPC.WebPage webpage = webPages.get(pendingId);
                                    if (delegateToRemove[0] != null) {
                                        NotificationCenter.getInstance(currentAccount).addObserver(delegateToRemove[0], NotificationCenter.didReceivedWebpagesInUpdates);
                                        delegateToRemove[0] = null;
                                    }
                                    whenLoaded.run(webpage instanceof TLRPC.TL_webPage ? webpage : null);
                                }
                            }
                        }
                    };
                    delegateToRemove[0] = delegate;
                    NotificationCenter.getInstance(currentAccount).addObserver(delegate, NotificationCenter.didReceivedWebpagesInUpdates);
                } else {
                    whenLoaded.run(webPage instanceof TLRPC.TL_webPage ? webPage : null);
                }
            } else {
                whenLoaded.run(null);
            }
        });
        return () -> { // cancel
            if (reqId[0] >= 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId[0], true);
                reqId[0] = -1;
            }
            if (delegateToRemove[0] != null) {
                NotificationCenter.getInstance(currentAccount).addObserver(delegateToRemove[0], NotificationCenter.didReceivedWebpagesInUpdates);
                delegateToRemove[0] = null;
            }
        };
    }

    private boolean openedDialogsActivity = false;
    private boolean sent = false;
    private final Utilities.Callback2<String, ArrayList<Long>> whenDone;

    public BotShareSheet(
        Context context,
        int currentAccount,
        long botId,
        TLRPC.TL_messages_preparedInlineMessage message,
        File file,
        TLRPC.WebPage webPage,
        Theme.ResourcesProvider resourcesProvider,
        Runnable whenOpened,
        Utilities.Callback2<String, ArrayList<Long>> whenDone
    ) {
        super(context, null, false, false, false, resourcesProvider);
        this.currentAccount = currentAccount;
        this.message = message;
        this.botId = botId;
        this.botName = UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(botId));
        this.whenDone = whenDone;

        setSlidingActionBar();
        headerPaddingTop = dp(4);
        headerPaddingBottom = dp(-10);

        messageObject = convert(currentAccount, botId, message.result, file, webPage);

        actionCell = new ChatActionCell(context, false, resourcesProvider);
        actionCell.setDelegate(new ChatActionCell.ChatActionCellDelegate() {});
        actionCell.setCustomText(LocaleController.getString(R.string.BotShareMessagePreview));

        messageCell = new ChatMessageCell(context, currentAccount) {
            @Override
            public boolean isDrawSelectionBackground() {
                return false;
            }
        };
        messageCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            @Override
            public boolean canPerformActions() {
                return false;
            }
        });
        messageCell.setMessageObject(messageObject, null, false, false, false);

        chatListView = new LinearLayout(context);
        chatListView.setOrientation(LinearLayout.VERTICAL);

        chatListView.addView(actionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        chatListView.addView(messageCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        chatView = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }
            @Override
            protected boolean isStatusBarVisible() {
                return false;
            }
            @Override
            protected boolean useRootView() {
                return false;
            }
        };
        chatView.setBackgroundImage(PreviewView.getBackgroundDrawable(null, currentAccount, botId, Theme.isCurrentThemeDark()), false);
        chatView.addView(chatListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 4, 8, 4, 8));

        buttonContainer = new FrameLayout(context);

        button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button.setText(LocaleController.getString(R.string.BotShareMessageShare), false);
        button.setOnClickListener(v -> {
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment == null) return;

            openedDialogsActivity = true;
            final Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("canSelectTopics", true);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_BOT_SHARE);

            if (!message.peer_types.isEmpty()) {
                args.putBoolean("allowGroups", false);
                args.putBoolean("allowMegagroups", false);
                args.putBoolean("allowLegacyGroups", false);
                args.putBoolean("allowUsers", false);
                args.putBoolean("allowChannels", false);
                args.putBoolean("allowBots", false);
                for (TLRPC.InlineQueryPeerType peerType : message.peer_types) {
                    if (peerType instanceof TLRPC.TL_inlineQueryPeerTypePM) {
                        args.putBoolean("allowUsers", true);
                    } else if (peerType instanceof TLRPC.TL_inlineQueryPeerTypeBotPM) {
                        args.putBoolean("allowBots", true);
                    } else if (peerType instanceof TLRPC.TL_inlineQueryPeerTypeBroadcast) {
                        args.putBoolean("allowChannels", true);
                    } else if (peerType instanceof TLRPC.TL_inlineQueryPeerTypeChat) {
                        args.putBoolean("allowLegacyGroups", true);
                    } else if (peerType instanceof TLRPC.TL_inlineQueryPeerTypeMegagroup) {
                        args.putBoolean("allowMegagroups", true);
                    }
                }
            }

            final DialogsActivity fragment = new DialogsActivity(args) {
                @Override
                public boolean clickSelectsDialog() {
                    return true;
                }
                @Override
                public void onFragmentDestroy() {
                    super.onFragmentDestroy();
                    if (!sent) {
                        sent = true;
                        if (whenDone != null) {
                            whenDone.run("USER_DECLINED", null);
                        }
                    }
                }
            };
            fragment.setDelegate((fragment1, dids, _message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                ArrayList<Long> dialogIds = new ArrayList<>();
                for (MessagesStorage.TopicKey key : dids) {
                    final long dialogId = key.dialogId;
                    final long topicId = key.topicId;

                    if (DialogObject.isEncryptedDialog(dialogId)) {
                        continue;
                    }

                    MessageObject replyToMsg = null;
                    if (topicId != 0) {
                        TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(-dialogId, topicId);
                        if (topic != null && topic.topicStartMessage != null) {
                            replyToMsg = new MessageObject(currentAccount, topic.topicStartMessage, false, false);
                            replyToMsg.isTopicMainMessage = true;
                        }
                    }

                    HashMap<String, String> params = new HashMap<>();
                    params.put("query_id", "" + message.query_id);
                    params.put("id", "" + message.result.id);
                    params.put("bot", "" + botId);
                    SendMessagesHelper.prepareSendingBotContextResult(lastFragment, AccountInstance.getInstance(currentAccount), message.result, params, dialogId, replyToMsg, replyToMsg, null, null, notify, scheduleDate, 0, null, 0, 0);
                    if (_message != null) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(_message.toString(), dialogId, replyToMsg, replyToMsg, null, true, null, null, null, true, 0, 0, null, false));
                    }
                    dialogIds.add(dialogId);
                }
                if (!sent) {
                    sent = true;
                    if (whenDone != null) {
                        whenDone.run(dialogIds.size() > 0 ? null : "USER_DECLINED", dialogIds);
                    }
                }
                if (topicsFragment != null) {
                    topicsFragment.finishFragment();
                    fragment1.removeSelfFromStack();
                } else {
                    fragment1.finishFragment();
                }
                return true;
            });
            lastFragment.presentFragment(fragment);
            dismiss();
            if (whenOpened != null) {
                whenOpened.run();
            }
        });
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10, 10, 10));

        containerView.addView(buttonContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(10 + 48 + 10) + 1);
        recyclerListView.setSections();

        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fixNavigationBar(getThemedColor(Theme.key_windowBackgroundGray));

        adapter.update(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int itemsCount = recyclerListView.getAdapter() == null ? 0 : recyclerListView.getAdapter().getItemCount();
        recyclerListView.scrollToPosition(Math.max(itemsCount - 1, 0));
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (!openedDialogsActivity && !sent) {
            sent = true;
            if (whenDone != null) {
                whenDone.run("USER_DECLINED", null);
            }
        }
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.BotShareMessage);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(-1, chatView));
        items.add(UItem.asShadow(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotShareMessageInfo, botName))));
    }

    public static MessageObject convert(int currentAccount, long botId, TLRPC.BotInlineResult result, File file, TLRPC.WebPage webPage) {
        if (file == null || !file.exists() || webPage != null)
            return convert(currentAccount, botId, result, null, null, webPage);

        final String type = result.type;
        final String finalPath = file.getAbsolutePath();

        TLRPC.Document document = null;
        TLRPC.TL_photo photo = null;
        switch (type) {
            case "audio":
            case "voice":
            case "file":
            case "video":
            case "sticker":
            case "gif": {
                document = new TLRPC.TL_document();
                document.id = 0;
                document.size = 0;
                document.dc_id = 0;
                document.mime_type = result.content.mime_type;
                document.file_reference = new byte[0];
                document.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                document.attributes.add(fileName);

                switch (type) {
                    case "gif": {
                        fileName.file_name = "animation.gif";
                        if (finalPath.endsWith("mp4")) {
                            document.mime_type = "video/mp4";
                            document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                        } else {
                            document.mime_type = "image/gif";
                        }
                        break;
                    }
                    case "voice": {
                        TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                        audio.duration = MessageObject.getInlineResultDuration(result);
                        audio.voice = true;
                        fileName.file_name = "audio.ogg";
                        document.attributes.add(audio);
                        break;
                    }
                    case "audio": {
                        TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                        audio.duration = MessageObject.getInlineResultDuration(result);
                        audio.title = result.title;
                        audio.flags |= 1;
                        if (result.description != null) {
                            audio.performer = result.description;
                            audio.flags |= 2;
                        }
                        fileName.file_name = "audio.mp3";
                        document.attributes.add(audio);
                        break;
                    }
                    case "file": {
                        int idx = result.content.mime_type.lastIndexOf('/');
                        if (idx != -1) {
                            fileName.file_name = "file." + result.content.mime_type.substring(idx + 1);
                        } else {
                            fileName.file_name = "file";
                        }
                        break;
                    }
                    case "video": {
                        fileName.file_name = "video.mp4";
                        TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
                        int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                        attributeVideo.w = wh[0];
                        attributeVideo.h = wh[1];
                        attributeVideo.duration = MessageObject.getInlineResultDuration(result);
                        attributeVideo.supports_streaming = true;
                        document.attributes.add(attributeVideo);
                        try {
                            if (result.thumb != null) {
                                String thumbPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb.url) + "." + ImageLoader.getHttpUrlExtension(result.thumb.url, "jpg")).getAbsolutePath();
                                Bitmap bitmap = ImageLoader.loadBitmap(thumbPath, null, 90, 90, true);
                                if (bitmap != null) {
                                    TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, false);
                                    if (thumb != null) {
                                        document.thumbs.add(thumb);
                                        document.flags |= 1;
                                    }
                                    bitmap.recycle();
                                }
                            }
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                        break;
                    }
                    case "sticker": {
                        TLRPC.TL_documentAttributeSticker attributeSticker = new TLRPC.TL_documentAttributeSticker();
                        attributeSticker.alt = "";
                        attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                        document.attributes.add(attributeSticker);
                        TLRPC.TL_documentAttributeImageSize attributeImageSize = new TLRPC.TL_documentAttributeImageSize();
                        int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                        attributeImageSize.w = wh[0];
                        attributeImageSize.h = wh[1];
                        document.attributes.add(attributeImageSize);
                        fileName.file_name = "sticker.webp";
                        try {
                            if (result.thumb != null) {
                                String thumbPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb.url) + "." + ImageLoader.getHttpUrlExtension(result.thumb.url, "webp")).getAbsolutePath();
                                Bitmap bitmap = ImageLoader.loadBitmap(thumbPath, null, 90, 90, true);
                                if (bitmap != null) {
                                    TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, false);
                                    if (thumb != null) {
                                        document.thumbs.add(thumb);
                                        document.flags |= 1;
                                    }
                                    bitmap.recycle();
                                }
                            }
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                        break;
                    }
                }
                if (fileName.file_name == null) {
                    fileName.file_name = "file";
                }
                if (document.mime_type == null) {
                    document.mime_type = "application/octet-stream";
                }
                if (document.thumbs.isEmpty()) {
                    TLRPC.PhotoSize thumb = new TLRPC.TL_photoSize();
                    int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                    thumb.w = wh[0];
                    thumb.h = wh[1];
                    thumb.size = 0;
                    thumb.location = new TLRPC.TL_fileLocationUnavailable();
                    thumb.type = "x";

                    document.thumbs.add(thumb);
                    document.flags |= 1;
                }
                break;
            }
            case "photo": {
                if (file.exists()) {
                    photo = SendMessagesHelper.getInstance(currentAccount).generatePhotoSizes(finalPath, null);
                }
                if (photo == null) {
                    photo = new TLRPC.TL_photo();
                    photo.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                    photo.file_reference = new byte[0];
                    TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                    int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                    photoSize.w = wh[0];
                    photoSize.h = wh[1];
                    photoSize.size = 1;
                    photoSize.location = new TLRPC.TL_fileLocationUnavailable();
                    photoSize.type = "x";
                    photo.sizes.add(photoSize);
                }
                break;
            }
        }
        return convert(currentAccount, botId, result, photo, document, null);
    }

    public static MessageObject convert(int currentAccount, long botId, TLRPC.BotInlineResult result, TLRPC.Photo photo, TLRPC.Document document, TLRPC.WebPage webPage) {
        if (photo == null) photo = result.photo;
        if (document == null) document = result.document;

        final TLRPC.TL_message msg = new TLRPC.TL_message();

        msg.out = false;
        msg.flags |= 2048;
        msg.via_bot_id = botId;
        msg.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();

        msg.peer_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
        msg.from_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());

        if (result.send_message != null) {
            final TLRPC.BotInlineMessage message = result.send_message;
            if (message instanceof TLRPC.TL_botInlineMessageText) {
                TLRPC.TL_botInlineMessageText m = (TLRPC.TL_botInlineMessageText) message;
                msg.message = m.message;
                msg.entities = m.entities;
            } else if (message instanceof TLRPC.TL_botInlineMessageMediaContact) {
                TLRPC.TL_botInlineMessageMediaContact m = (TLRPC.TL_botInlineMessageMediaContact) message;
                TLRPC.TL_messageMediaContact media = new TLRPC.TL_messageMediaContact();
                media.phone_number = m.phone_number;
                media.first_name = m.first_name;
                media.last_name = m.last_name;
                media.vcard = m.vcard;
                msg.flags |= 512;
                msg.media = media;
            } else if (message instanceof TLRPC.TL_botInlineMessageMediaGeo) {
                TLRPC.TL_botInlineMessageMediaGeo m = (TLRPC.TL_botInlineMessageMediaGeo) message;
                TLRPC.TL_messageMediaGeo media = new TLRPC.TL_messageMediaGeo();
                media.geo = m.geo;
                msg.flags |= 512;
                msg.media = media;
            } else if (message instanceof TLRPC.TL_botInlineMessageMediaVenue) {
                TLRPC.TL_botInlineMessageMediaVenue m = (TLRPC.TL_botInlineMessageMediaVenue) message;
                TLRPC.TL_messageMediaVenue media = new TLRPC.TL_messageMediaVenue();
                media.geo = m.geo;
                media.title = m.title;
                media.address = m.address;
                media.provider = m.provider;
                media.venue_id = m.venue_id;
                media.provider = m.venue_type;
                msg.flags |= 512;
                msg.media = media;
            } else if (message instanceof TLRPC.TL_botInlineMessageMediaAuto) {
                final TLRPC.TL_botInlineMessageMediaAuto m = (TLRPC.TL_botInlineMessageMediaAuto) message;
                msg.message = m.message;
                if (TLObject.hasFlag(m.flags, TLObject.FLAG_1)) {
                    msg.flags |= TLObject.FLAG_7;
                    msg.entities = m.entities;
                }
            } else if (message instanceof TLRPC.TL_botInlineMessageMediaInvoice) {
                final TLRPC.TL_botInlineMessageMediaInvoice m = (TLRPC.TL_botInlineMessageMediaInvoice) message;
                final TLRPC.TL_messageMediaInvoice media = new TLRPC.TL_messageMediaInvoice();
                media.shipping_address_requested = m.shipping_address_requested;
                media.test = m.test;
                media.title = m.title;
                media.description = m.description;
                if (TLObject.hasFlag(m.flags, TLObject.FLAG_0)) {
                    media.flags |= TLObject.FLAG_7;
                    media.webPhoto = m.photo;
                }
                media.currency = m.currency;
                media.total_amount = m.total_amount;
                msg.flags |= 512;
                msg.media = media;
            } else if (message instanceof TLRPC.TL_botInlineMessageMediaWebPage) {
                final TLRPC.TL_botInlineMessageMediaWebPage m = (TLRPC.TL_botInlineMessageMediaWebPage) message;
                final TLRPC.TL_messageMediaWebPage media = new TLRPC.TL_messageMediaWebPage();
                media.force_large_media = m.force_large_media;
                media.force_small_media = m.force_small_media;
                media.manual = m.manual;
                media.safe = m.safe;
                msg.invert_media = m.invert_media;
                msg.message = m.message;
                if (webPage != null) {
                    media.webpage = webPage;
                } else {
                    final TLRPC.TL_webPage webpage = new TLRPC.TL_webPage();
                    if (TLObject.hasFlag(m.flags, TLObject.FLAG_1)) {
                        msg.flags |= TLObject.FLAG_7;
                        msg.entities = m.entities;
                    }
                    webpage.url = webpage.display_url = m.url;
                    media.webpage = webpage;
                }
                msg.flags |= 512;
                msg.media = media;
            } else if (message instanceof TLRPC.TL_botInlineMessageRichMessage) {
                final TLRPC.TL_botInlineMessageRichMessage m = (TLRPC.TL_botInlineMessageRichMessage) message;
                msg.flags2 |= TLObject.FLAG_13;
                msg.rich_message = m.rich_message;
            }
        }

        if (photo != null) {
            TLRPC.TL_messageMediaPhoto media = new TLRPC.TL_messageMediaPhoto();
            media.photo = photo;
            msg.flags |= 512;
            msg.media = media;
        } else if (document != null) {
            TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
            media.flags |= 1;
            media.voice = "voice".equalsIgnoreCase(result.type);
            media.round = "round".equalsIgnoreCase(result.type);
            media.document = document;
            msg.flags |= 512;
            msg.media = media;
        }

        if (result.send_message != null && result.send_message.reply_markup != null) {
            msg.flags |= 64;
            msg.reply_markup = result.send_message.reply_markup;
        }

        final MessageObject messageObject = new MessageObject(currentAccount, msg, true, true) {
            @Override
            public boolean isOut() {
                return false;
            }
            @Override
            public boolean isOutOwner() {
                return false;
            }
        };
        return messageObject;
    }

}
