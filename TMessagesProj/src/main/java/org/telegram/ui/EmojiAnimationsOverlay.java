package org.telegram.ui;

import android.graphics.Canvas;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.Reactions.AnimatedEmojiEffect;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerSetBulletinLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Stories.StoryReactionWidgetView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class EmojiAnimationsOverlay implements NotificationCenter.NotificationCenterDelegate {

    private final int ANIMATION_JSON_VERSION = 1;
    private final String INTERACTIONS_STICKER_PACK = "EmojiAnimations";

    @Nullable
    ChatActivity chatActivity;
    int currentAccount;
    TLRPC.TL_messages_stickerSet set;
    boolean inited = false;
    HashMap<String, ArrayList<TLRPC.Document>> emojiInteractionsStickersMap = new HashMap<>();
    HashMap<Long, Integer> lastAnimationIndex = new HashMap<>();
    Random random = new Random();
    private boolean attached;

    int lastTappedMsgId = -1;
    long lastTappedTime = 0;
    String lastTappedEmoji;
    ArrayList<Long> timeIntervals = new ArrayList<>();
    ArrayList<Integer> animationIndexes = new ArrayList<>();
    Runnable sentInteractionsRunnable;

    Runnable hintRunnable;
    private final static HashSet<String> supportedEmoji = new HashSet<>();
    private final static HashSet<String> excludeEmojiFromPack = new HashSet<>();

    static {
        // 1️⃣, 2️⃣, 3️⃣... etc
        excludeEmojiFromPack.add("\u0030\u20E3");
        excludeEmojiFromPack.add("\u0031\u20E3");
        excludeEmojiFromPack.add("\u0032\u20E3");
        excludeEmojiFromPack.add("\u0033\u20E3");
        excludeEmojiFromPack.add("\u0034\u20E3");
        excludeEmojiFromPack.add("\u0035\u20E3");
        excludeEmojiFromPack.add("\u0036\u20E3");
        excludeEmojiFromPack.add("\u0037\u20E3");
        excludeEmojiFromPack.add("\u0038\u20E3");
        excludeEmojiFromPack.add("\u0039\u20E3");
    }

    ArrayList<DrawingObject> drawingObjects = new ArrayList<>();

    FrameLayout contentLayout;
    RecyclerListView listView;
    long dialogId;
    long threadMsgId;

    public EmojiAnimationsOverlay(FrameLayout frameLayout, int currentAccount) {
        this.contentLayout = frameLayout;
        this.currentAccount = currentAccount;
    }

    public EmojiAnimationsOverlay(ChatActivity chatActivity, FrameLayout frameLayout, RecyclerListView chatListView, int currentAccount, long dialogId, long threadMsgId) {
        this.chatActivity = chatActivity;
        this.contentLayout = frameLayout;
        this.listView = chatListView;
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.threadMsgId = threadMsgId;
    }

    public void onAttachedToWindow() {
        attached = true;
        checkStickerPack();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.onEmojiInteractionsReceived);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        for (int i = 0; i < drawingObjects.size(); i++) {
            drawingObjects.get(i).imageReceiver.onAttachedToWindow();
            if (drawingObjects.get(i).genericEffect != null) {
                drawingObjects.get(i).genericEffect.setView(contentLayout);
            }
        }
    }

    public void onDetachedFromWindow() {
        attached = false;
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.onEmojiInteractionsReceived);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        for (int i = 0; i < drawingObjects.size(); i++) {
            drawingObjects.get(i).imageReceiver.onDetachedFromWindow();
            if (drawingObjects.get(i).genericEffect != null) {
                drawingObjects.get(i).genericEffect.removeView(contentLayout);
            }
        }
        drawingObjects.clear();
    }


    public void clear() {
        for (int i = 0; i < drawingObjects.size(); i++) {
            drawingObjects.get(i).imageReceiver.onDetachedFromWindow();
            if (drawingObjects.get(i).genericEffect != null) {
                drawingObjects.get(i).genericEffect.removeView(contentLayout);
            }
        }
        drawingObjects.clear();
    }

    public void checkStickerPack() {
        if (inited) {
            return;
        }
        set = MediaDataController.getInstance(currentAccount).getStickerSetByName(INTERACTIONS_STICKER_PACK);
        if (set == null) {
            set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(INTERACTIONS_STICKER_PACK);
        }
        if (set == null) {
            MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(INTERACTIONS_STICKER_PACK, false, true);
        }
        if (set != null) {
            HashMap<Long, TLRPC.Document> stickersMap = new HashMap<>();
            for (int i = 0; i < set.documents.size(); i++) {
                stickersMap.put(set.documents.get(i).id, set.documents.get(i));
            }
            for (int i = 0; i < set.packs.size(); i++) {
                TLRPC.TL_stickerPack pack = set.packs.get(i);
                if (!excludeEmojiFromPack.contains(pack.emoticon) && pack.documents.size() > 0) {
                    supportedEmoji.add(pack.emoticon);
                    ArrayList<TLRPC.Document> stickers = new ArrayList<>();
                    emojiInteractionsStickersMap.put(pack.emoticon, stickers);
                    for (int j = 0; j < pack.documents.size(); j++) {
                        stickers.add(stickersMap.get(pack.documents.get(j)));
                    }

                    if (pack.emoticon.equals("❤")) {
                        String[] heartEmojies = new String[]{"🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎"};
                        for (String heart : heartEmojies) {
                            supportedEmoji.add(heart);
                            emojiInteractionsStickersMap.put(heart, stickers);
                        }
                    }
                }
            }
            inited = true;
        }
    }


    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.diceStickersDidLoad) {
            String name = (String) args[0];
            if (INTERACTIONS_STICKER_PACK.equals(name)) {
                checkStickerPack();
            }
        } else if (id == NotificationCenter.onEmojiInteractionsReceived) {
            if (chatActivity == null) {
                return;
            }
            long dialogId = (long) args[0];
            TLRPC.TL_sendMessageEmojiInteraction action = (TLRPC.TL_sendMessageEmojiInteraction) args[1];
            if (dialogId == this.dialogId && supportedEmoji.contains(action.emoticon)) {
                int messageId = action.msg_id;
                if (action.interaction.data != null) {
                    try {
                        JSONObject jsonObject = new JSONObject(action.interaction.data);
                        JSONArray array = jsonObject.getJSONArray("a");
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject actionObject = array.getJSONObject(i);
                            int animation = actionObject.optInt("i", 1) - 1;
                            double time = actionObject.optDouble("t", 0.0);
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    findViewAndShowAnimation(messageId, animation);
                                }
                            }, (long) (time * 1000));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }
        } else if (id == NotificationCenter.updateInterfaces) {
            Integer printingType = MessagesController.getInstance(currentAccount).getPrintingStringType(dialogId, threadMsgId);
            if (printingType != null && printingType == 5) {
                cancelHintRunnable();
            }
        }
    }

    public boolean supports(String emoticon) {
        return emojiInteractionsStickersMap.containsKey(unwrapEmoji(emoticon));
    }

    private void findViewAndShowAnimation(int messageId, int animation) {
        if (!attached) {
            return;
        }
        ChatMessageCell bestView = null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) child;
                String stickerEmoji = cell.getMessageObject().getStickerEmoji();
                if (stickerEmoji == null) {
                    stickerEmoji = cell.getMessageObject().messageOwner.message;
                }
                if (cell.getPhotoImage().hasNotThumb() && stickerEmoji != null) {
                    if (cell.getMessageObject().getId() == messageId) {
                        bestView = cell;
                        break;
                    }
                }
            }
        }

        if (bestView != null && chatActivity != null) {
            chatActivity.restartSticker(bestView);
            if (!EmojiData.hasEmojiSupportVibration(bestView.getMessageObject().getStickerEmoji()) && !bestView.getMessageObject().isPremiumSticker() && !bestView.getMessageObject().isAnimatedAnimatedEmoji()) {
                bestView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            showAnimationForCell(bestView, animation, false, true);
        }
    }

    public void draw(Canvas canvas) {
        if (!drawingObjects.isEmpty()) {
            for (int i = 0; i < drawingObjects.size(); i++) {
                DrawingObject drawingObject = drawingObjects.get(i);
                if (chatActivity != null) {
                    drawingObject.viewFound = false;
                    float childY = 0;
                    for (int k = 0; k < listView.getChildCount(); k++) {
                        View child = listView.getChildAt(k);
                        ImageReceiver photoImage = null;
                        MessageObject messageObject = null;
                        if (child instanceof ChatMessageCell) {
                            ChatMessageCell cell = (ChatMessageCell) child;
                            messageObject = cell.getMessageObject();
                            photoImage = cell.getPhotoImage();
                        } else if (child instanceof ChatActionCell) {
                            ChatActionCell cell = (ChatActionCell) child;
                            messageObject = cell.getMessageObject();
                            photoImage = cell.getPhotoImage();
                        }
                        if (messageObject != null && messageObject.getId() == drawingObject.messageId) {
                            drawingObject.viewFound = true;
                            float viewX = listView.getX() + child.getX();
                            float viewY = listView.getY() + child.getY();
                            childY = child.getY();
                            if (drawingObject.isPremiumSticker) {
                                drawingObject.lastX = viewX + photoImage.getImageX();
                                drawingObject.lastY = viewY + photoImage.getImageY();
                            } else {
                                viewX += photoImage.getImageX();
                                viewY += photoImage.getImageY();
                                if (drawingObject.isOut) {
                                    viewX += -photoImage.getImageWidth() * 2 + AndroidUtilities.dp(24);
                                } else {
                                    viewX += -AndroidUtilities.dp(24);
                                }
                                viewY -= photoImage.getImageWidth();
                                drawingObject.lastX = viewX;
                                drawingObject.lastY = viewY;
                            }
                            drawingObject.lastW = photoImage.getImageWidth();
                            drawingObject.lastH = photoImage.getImageHeight();
                            break;
                        }
                    }

                    if (!drawingObject.viewFound || childY + drawingObject.lastH < chatActivity.getChatListViewPadding() || childY > listView.getMeasuredHeight() - chatActivity.blurredViewBottomOffset) {
                        drawingObject.removing = true;
                    }

                    if (drawingObject.isPremiumSticker) {
                        float halfStickerHeight = drawingObject.lastH / 2f;
                        boolean outsideDown = listView.getMeasuredHeight() - childY <= halfStickerHeight;
                        boolean outsideUp = childY - chatActivity.getChatListViewPadding() + halfStickerHeight <= 0;
                        if (outsideDown || outsideUp) {
                            drawingObject.removing = true;
                        }
                    }

                    if (drawingObject.removing && drawingObject.removeProgress != 1f) {
                        drawingObject.removeProgress = Utilities.clamp(drawingObject.removeProgress + 16 / 150f, 1f, 0);
                        drawingObject.imageReceiver.setAlpha(1f - drawingObject.removeProgress);
                        chatActivity.contentView.invalidate();
                    }
                }

                boolean removeOnStart = !drawingObject.wasPlayed && drawingObject.removing;
                if (!removeOnStart) {
                    if (drawingObject.isPremiumSticker) {
                        float size = drawingObject.lastH * 1.49926f;
                        float paddingHorizontal = size * 0.0546875f;
                        float centerY = drawingObject.lastY + drawingObject.lastH / 2f;
                        float top = centerY - size / 2f - size * 0.00279f;
                        if (!drawingObject.isOut) {
                            drawingObject.imageReceiver.setImageCoords(drawingObject.lastX - paddingHorizontal, top, size, size);
                        } else {
                            drawingObject.imageReceiver.setImageCoords(drawingObject.lastX + drawingObject.lastW - size + paddingHorizontal, top, size, size);
                        }

                        if (!drawingObject.isOut) {
                            canvas.save();
                            canvas.scale(-1f, 1, drawingObject.imageReceiver.getCenterX(), drawingObject.imageReceiver.getCenterY());
                            drawingObject.imageReceiver.draw(canvas);
                            canvas.restore();
                        } else {
                            drawingObject.imageReceiver.draw(canvas);
                        }
                    } else {
                        if (drawingObject.genericEffect != null) {
                            float x = drawingObject.lastX + drawingObject.randomOffsetX;
                            float y = drawingObject.lastY + drawingObject.randomOffsetY;
                            float size = drawingObject.lastW * 3;
                            drawingObject.genericEffect.setBounds((int) x, (int) y, (int) (x + size), (int) (y + size));
                            drawingObject.genericEffect.draw(canvas);
                        } else {
                            drawingObject.imageReceiver.setImageCoords(drawingObject.lastX + drawingObject.randomOffsetX, drawingObject.lastY + drawingObject.randomOffsetY, drawingObject.lastW * 3, drawingObject.lastW * 3);
                            if (!drawingObject.isOut) {
                                canvas.save();
                                canvas.scale(-1f, 1, drawingObject.imageReceiver.getCenterX(), drawingObject.imageReceiver.getCenterY());
                                drawingObject.imageReceiver.draw(canvas);
                                canvas.restore();
                            } else {
                                drawingObject.imageReceiver.draw(canvas);
                            }
                        }
                    }
                }

                boolean isDone;
                if (drawingObject.genericEffect != null) {
                    isDone = drawingObject.genericEffect.isDone();
                } else {
                    isDone = (drawingObject.wasPlayed && drawingObject.imageReceiver.getLottieAnimation() != null && drawingObject.imageReceiver.getLottieAnimation().getCurrentFrame() >= drawingObject.imageReceiver.getLottieAnimation().getFramesCount() - 2);
                }
                if (drawingObject.removeProgress == 1f || isDone || removeOnStart) {
                    DrawingObject toRemove = drawingObjects.remove(i);
                    if (drawingObject.isPremiumSticker && drawingObject.imageReceiver.getLottieAnimation() != null) {
                        toRemove.imageReceiver.getLottieAnimation().setCurrentFrame(0, true, true);
                    }
                    toRemove.imageReceiver.onDetachedFromWindow();
                    if (toRemove.genericEffect != null) {
                        toRemove.genericEffect.removeView(contentLayout);
                    }
                    i--;
                } else if (drawingObject.imageReceiver.getLottieAnimation() != null && drawingObject.imageReceiver.getLottieAnimation().isRunning()) {
                    drawingObject.wasPlayed = true;
                } else if (drawingObject.imageReceiver.getLottieAnimation() != null && !drawingObject.imageReceiver.getLottieAnimation().isRunning()) {
                    drawingObject.imageReceiver.getLottieAnimation().setCurrentFrame(0, true);
                    drawingObject.imageReceiver.getLottieAnimation().start();
                }
            }
            if (drawingObjects.isEmpty()) {
                onAllEffectsEnd();
            }
            contentLayout.invalidate();
        }
    }

    public void onAllEffectsEnd() {

    }

    public boolean onTapItem(ChatMessageCell view, ChatActivity chatActivity, boolean userTapped) {
        if (chatActivity.isSecretChat() || view.getMessageObject() == null || view.getMessageObject().getId() < 0) {
            return false;
        }
        if (!view.getMessageObject().isPremiumSticker() && chatActivity.currentUser == null) {
            return false;
        }
        boolean show = showAnimationForCell(view, -1, userTapped, false);

        if (userTapped && show && !EmojiData.hasEmojiSupportVibration(view.getMessageObject().getStickerEmoji()) && !view.getMessageObject().isPremiumSticker() && !view.getMessageObject().isAnimatedAnimatedEmoji()) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (view.getMessageObject().isPremiumSticker() || (!userTapped && view.getMessageObject().isAnimatedEmojiStickerSingle())) {
            view.getMessageObject().forcePlayEffect = false;
            view.getMessageObject().messageOwner.premiumEffectWasPlayed = true;
            chatActivity.getMessagesStorage().updateMessageCustomParams(dialogId, view.getMessageObject().messageOwner);
            return show;
        }
        Integer printingType = MessagesController.getInstance(currentAccount).getPrintingStringType(dialogId, threadMsgId);
        boolean canShowHint = true;
        if (printingType != null && printingType == 5) {
            canShowHint = false;
        }
        if (canShowHint && hintRunnable == null && show && (Bulletin.getVisibleBulletin() == null || !Bulletin.getVisibleBulletin().isShowing()) && SharedConfig.emojiInteractionsHintCount > 0 && UserConfig.getInstance(currentAccount).getClientUserId() != chatActivity.currentUser.id) {
            SharedConfig.updateEmojiInteractionsHintCount(SharedConfig.emojiInteractionsHintCount - 1);
            TLRPC.Document document;
            if (view.getMessageObject().isAnimatedAnimatedEmoji()) {
                document = view.getMessageObject().getDocument();
            } else {
                document = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(view.getMessageObject().getStickerEmoji());
            }
            StickerSetBulletinLayout layout = new StickerSetBulletinLayout(chatActivity.getParentActivity(), null, StickerSetBulletinLayout.TYPE_EMPTY, document, chatActivity.getResourceProvider());
            layout.subtitleTextView.setVisibility(View.GONE);
            layout.titleTextView.setText(Emoji.replaceEmoji(AndroidUtilities.replaceTags(LocaleController.formatString("EmojiInteractionTapHint", R.string.EmojiInteractionTapHint, chatActivity.currentUser.first_name)), layout.titleTextView.getPaint().getFontMetricsInt(), false));
            layout.titleTextView.setTypeface(null);
            layout.titleTextView.setMaxLines(3);
            layout.titleTextView.setSingleLine(false);
            Bulletin bulletin = Bulletin.make(chatActivity, layout, Bulletin.DURATION_LONG);
            AndroidUtilities.runOnUIThread(hintRunnable = new Runnable() {
                @Override
                public void run() {
                    bulletin.show();
                    hintRunnable = null;
                }
            }, 1500);
        }
        return show;
    }

    public void cancelHintRunnable() {
        if (hintRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hintRunnable);
        }
        hintRunnable = null;
    }

    public boolean showAnimationForActionCell(ChatActionCell view, TLRPC.Document document, TLRPC.VideoSize videoSize) {
        if (drawingObjects.size() > 12) {
            return false;
        }
        if (!view.getPhotoImage().hasNotThumb()) {
            return false;
        }
        float imageH = view.getPhotoImage().getImageHeight();
        float imageW = view.getPhotoImage().getImageWidth();
        if (imageH <= 0 || imageW <= 0) {
            return false;
        }

        int sameAnimationsCountMessageId = 0;
        int sameAnimationsCountDocumentId = 0;
        for (int i = 0; i < drawingObjects.size(); i++) {
            if (drawingObjects.get(i).messageId == view.getMessageObject().getId()) {
                sameAnimationsCountMessageId++;
                if (drawingObjects.get(i).imageReceiver.getLottieAnimation() == null || drawingObjects.get(i).imageReceiver.getLottieAnimation().isGeneratingCache()) {
                    return false;
                }
            }
            if (drawingObjects.get(i).document != null && document != null && drawingObjects.get(i).document.id == document.id) {
                sameAnimationsCountDocumentId++;
            }
        }
        if (sameAnimationsCountMessageId >= 4) {
            return false;
        }

        DrawingObject drawingObject = new DrawingObject();
        drawingObject.isPremiumSticker = true;
        drawingObject.randomOffsetX = imageW / 4 * ((random.nextInt() % 101) / 100f);
        drawingObject.randomOffsetY = imageH / 4 * ((random.nextInt() % 101) / 100f);
        drawingObject.messageId = view.getMessageObject().getId();
        drawingObject.isOut = true;
        drawingObject.imageReceiver.setAllowStartAnimation(true);
        int w = getFilterWidth();
        if (sameAnimationsCountDocumentId > 0) {
            Integer lastIndex = lastAnimationIndex.get(document.id);
            int currentIndex = lastIndex == null ? 0 : lastIndex;
            lastAnimationIndex.put(document.id, (currentIndex + 1) % 4);
            drawingObject.imageReceiver.setUniqKeyPrefix(currentIndex + "_" + drawingObject.messageId + "_");
        }
        drawingObject.document = document;
        drawingObject.imageReceiver.setImage(ImageLocation.getForDocument(videoSize, document), w + "_" + w, null, "tgs", set, 1);

        drawingObject.imageReceiver.setLayerNum(Integer.MAX_VALUE);
        drawingObject.imageReceiver.setAutoRepeat(0);
        if (drawingObject.imageReceiver.getLottieAnimation() != null) {
            if (drawingObject.isPremiumSticker) {
                drawingObject.imageReceiver.getLottieAnimation().setCurrentFrame(0, false, true);
            }
            drawingObject.imageReceiver.getLottieAnimation().start();
        }
        drawingObjects.add(drawingObject);
        if (attached) {
            drawingObject.imageReceiver.onAttachedToWindow();
            drawingObject.imageReceiver.setParentView(contentLayout);
        }
        contentLayout.invalidate();
        return true;
    }

    public void preloadAnimation(ChatMessageCell cell) {
        MessageObject messageObject = cell.getMessageObject();
        if (messageObject.isPremiumSticker()) {
            return;
        }
        String emoji = messageObject.getStickerEmoji();
        if (emoji == null) {
            emoji = messageObject.messageOwner.message;
        }
        emoji = unwrapEmoji(emoji);
        if (!supportedEmoji.contains(emoji)) {
            return;
        }
        ArrayList<TLRPC.Document> arrayList = emojiInteractionsStickersMap.get(emoji);
        if (arrayList == null || arrayList.isEmpty()) {
            return;
        }
        int preloadCount = Math.min(1, arrayList.size());
        for (int i = 0; i < preloadCount; ++i) {
            this.preloadAnimation(arrayList.get(i));
        }
    }

    private HashMap<Long, Boolean> preloaded;

    private void preloadAnimation(TLRPC.Document document) {
        if (document == null) {
            return;
        }
        if (preloaded != null && preloaded.containsKey(document.id)) {
            return;
        }
        if (preloaded == null) {
            preloaded = new HashMap<>();
        }
        preloaded.put(document.id, true);
        MediaDataController.getInstance(currentAccount).preloadImage(ImageLocation.getForDocument(document), FileLoader.PRIORITY_NORMAL_UP);
    }

    private boolean showAnimationForCell(ChatMessageCell view, int animation, boolean sendTap, boolean sendSeen) {
        if (drawingObjects.size() > 12) {
            return false;
        }
        if (!view.getPhotoImage().hasNotThumb()) {
            return false;
        }
        MessageObject messageObject = view.getMessageObject();
        String emoji = messageObject.getStickerEmoji();
        if (emoji == null) {
            emoji = messageObject.messageOwner.message;
        }
        if (emoji == null) {
            return false;
        }
        float imageH = view.getPhotoImage().getImageHeight();
        float imageW = view.getPhotoImage().getImageWidth();
        if (imageH <= 0 || imageW <= 0) {
            return false;
        }

        emoji = unwrapEmoji(emoji);

        int viewId = view.getMessageObject().getId();
        TLRPC.Document viewDocument = view.getMessageObject().getDocument();
        boolean isOutOwner = view.getMessageObject().isOutOwner();
        return createDrawingObject(emoji, viewId, viewDocument, messageObject, animation, sendTap, sendSeen, imageW, imageH, isOutOwner);
    }

    private boolean createDrawingObject(String emoji, int viewId, TLRPC.Document viewDocument, @Nullable MessageObject messageObject, int animation, boolean sendTap, boolean sendSeen, float imageW, float imageH, boolean isOutOwner) {
        boolean isPremiumSticker = messageObject != null && messageObject.isPremiumSticker();
        if (supportedEmoji.contains(emoji) || isPremiumSticker) {
            ArrayList<TLRPC.Document> arrayList = emojiInteractionsStickersMap.get(emoji);
            if ((arrayList != null && !arrayList.isEmpty()) || isPremiumSticker) {
                int sameAnimationsCountMessageId = 0;
                int sameAnimationsCountDocumentId = 0;
                for (int i = 0; i < drawingObjects.size(); i++) {
                    if (drawingObjects.get(i).messageId == viewId) {
                        sameAnimationsCountMessageId++;
                        if (drawingObjects.get(i).imageReceiver.getLottieAnimation() == null || drawingObjects.get(i).imageReceiver.getLottieAnimation().isGeneratingCache()) {
                            return false;
                        }
                    }
                    if (drawingObjects.get(i).document != null && viewDocument != null && drawingObjects.get(i).document.id == viewDocument.id) {
                        sameAnimationsCountDocumentId++;
                    }
                }
                if (sendTap && isPremiumSticker && sameAnimationsCountMessageId > 0) {
                    if (Bulletin.getVisibleBulletin() != null && Bulletin.getVisibleBulletin().hash == messageObject.getId()) {
                        return false;
                    }
                    TLRPC.InputStickerSet inputStickerSet = messageObject.getInputStickerSet();
                    TLRPC.TL_messages_stickerSet stickerSet = null;
                    if (inputStickerSet.short_name != null) {
                        stickerSet = MediaDataController.getInstance(currentAccount).getStickerSetByName(inputStickerSet.short_name);
                    }
                    if (stickerSet == null) {
                        stickerSet = MediaDataController.getInstance(currentAccount).getStickerSetById(inputStickerSet.id);
                    }
                    if (stickerSet == null) {
                        TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
                        req.stickerset = inputStickerSet;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            showStickerSetBulletin((TLRPC.TL_messages_stickerSet) response, messageObject);
                        }));
                    } else {
                        showStickerSetBulletin(stickerSet, messageObject);
                    }
                    return false;
                }
                if (sameAnimationsCountMessageId >= 4) {
                    return false;
                }
                TLRPC.Document document = null;
                TLRPC.VideoSize videoSize = null;
                if (isPremiumSticker) {
                    videoSize = messageObject.getPremiumStickerAnimation();
                } else if (messageObject != null && messageObject.isAnimatedAnimatedEmoji()) {
                    if (animation < 0 || animation > arrayList.size() - 1) {
                        ArrayList<Integer> preloadedVariants = new ArrayList<>();
                        for (int i = 0; i < arrayList.size(); ++i) {
                            TLRPC.Document d = arrayList.get(i);
                            if (d == null) {
                                continue;
                            }
                            Boolean value = preloaded != null ? preloaded.get(d.id) : null;
                            if (value != null && value) {
                                preloadedVariants.add(i);
                            }
                        }
                        if (preloadedVariants.isEmpty()) {
                            animation = Math.abs(random.nextInt()) % arrayList.size();
                        } else {
                            animation = preloadedVariants.get(Math.abs(random.nextInt()) % preloadedVariants.size());
                        }
                    }
                    document = arrayList.get(animation);
                } else {
                    if (animation < 0 || animation > arrayList.size() - 1) {
                        animation = Math.abs(random.nextInt()) % arrayList.size();
                    }
                    document = arrayList.get(animation);
                }

                if (document == null && videoSize == null) {
                    return false;
                }

                DrawingObject drawingObject = new DrawingObject();
                drawingObject.isPremiumSticker = messageObject == null ? false : messageObject.isPremiumSticker();
                drawingObject.randomOffsetX = imageW / 4 * ((random.nextInt() % 101) / 100f);
                drawingObject.randomOffsetY = imageH / 4 * ((random.nextInt() % 101) / 100f);
                drawingObject.messageId = viewId;
                drawingObject.document = document;
                drawingObject.isOut = isOutOwner;
                drawingObject.imageReceiver.setAllowStartAnimation(true);
                drawingObject.imageReceiver.setAllowLottieVibration(sendTap);
                int w;
                if (document != null) {
                    w = getFilterWidth();
                    Integer lastIndex = lastAnimationIndex.get(document.id);
                    int currentIndex = (lastIndex == null ? 0 : lastIndex) + 1;
                    lastAnimationIndex.put(document.id, currentIndex);
                    //currentIndex = currentIndex % 4;

                    ImageLocation imageLocation = ImageLocation.getForDocument(document);
                    drawingObject.imageReceiver.setUniqKeyPrefix(currentIndex + "_" + drawingObject.messageId + "_");

                    drawingObject.imageReceiver.setImage(imageLocation, w + "_" + w + "_pcache_compress", null, "tgs", set, 1);
                    drawingObject.imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
                        @Override
                        public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
                        }

                        @Override
                        public void onAnimationReady(ImageReceiver imageReceiver) {
                            if (sendTap && messageObject.isAnimatedAnimatedEmoji() && imageReceiver.getLottieAnimation() != null && !imageReceiver.getLottieAnimation().hasVibrationPattern()) {
                                contentLayout.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                            }
                        }
                    });
                    if (drawingObject.imageReceiver.getLottieAnimation() != null) {
                        drawingObject.imageReceiver.getLottieAnimation().setCurrentFrame(0, false, true);
                    }
                } else {
                    w = getFilterWidth();
                    if (sameAnimationsCountDocumentId > 0) {
                        Integer lastIndex = lastAnimationIndex.get(messageObject.getDocument().id);
                        int currentIndex = lastIndex == null ? 0 : lastIndex;
                        lastAnimationIndex.put(messageObject.getDocument().id, (currentIndex + 1) % 4);
                        drawingObject.imageReceiver.setUniqKeyPrefix(currentIndex + "_" + drawingObject.messageId + "_");
                    }
                    drawingObject.document = messageObject.getDocument();
                    drawingObject.imageReceiver.setImage(ImageLocation.getForDocument(videoSize, messageObject.getDocument()), w + "_" + w, null, "tgs", set, 1);
                }

                drawingObject.imageReceiver.setLayerNum(Integer.MAX_VALUE);
                drawingObject.imageReceiver.setAutoRepeat(0);
                if (drawingObject.imageReceiver.getLottieAnimation() != null) {
                    if (drawingObject.isPremiumSticker) {
                        drawingObject.imageReceiver.getLottieAnimation().setCurrentFrame(0, false, true);
                    }
                    drawingObject.imageReceiver.getLottieAnimation().start();
                }
                drawingObjects.add(drawingObject);
                drawingObject.imageReceiver.onAttachedToWindow();
                drawingObject.imageReceiver.setParentView(contentLayout);
                contentLayout.invalidate();

                if (sendTap && !isPremiumSticker && UserConfig.getInstance(currentAccount).clientUserId != dialogId) {
                    if (lastTappedMsgId != 0 && lastTappedMsgId != viewId) {
                        if (sentInteractionsRunnable != null) {
                            AndroidUtilities.cancelRunOnUIThread(sentInteractionsRunnable);
                            sentInteractionsRunnable.run();
                        }
                    }
                    lastTappedMsgId = viewId;
                    lastTappedEmoji = emoji;
                    if (lastTappedTime == 0) {
                        lastTappedTime = System.currentTimeMillis();
                        timeIntervals.clear();
                        animationIndexes.clear();
                        timeIntervals.add(0L);
                        animationIndexes.add(animation);
                    } else {
                        timeIntervals.add(System.currentTimeMillis() - lastTappedTime);
                        animationIndexes.add(animation);
                    }
                    if (sentInteractionsRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(sentInteractionsRunnable);
                        sentInteractionsRunnable = null;
                    }
                    AndroidUtilities.runOnUIThread(sentInteractionsRunnable = () -> {
                        sendCurrentTaps();
                        sentInteractionsRunnable = null;
                    }, 500);
                }

                if (sendSeen) {
                    MessagesController.getInstance(currentAccount).sendTyping(dialogId, threadMsgId, 11, emoji, 0);
                }
                return true;
            }
        }
        return false;
    }

    public static int getFilterWidth() {
        int w;
        if (AndroidUtilities.isTablet()) {
            w = (int) (AndroidUtilities.getMinTabletSide() * 0.4f);
        } else {
            w = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f);
        }
        return (int) (2f * w / AndroidUtilities.density);
    }

    private void showStickerSetBulletin(TLRPC.TL_messages_stickerSet stickerSet, MessageObject messageObject) {
        if (chatActivity == null) {
            return;
        }
        if (MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() || chatActivity.getParentActivity() == null) {
            return;
        }
        StickerSetBulletinLayout layout = new StickerSetBulletinLayout(contentLayout.getContext(), null, StickerSetBulletinLayout.TYPE_EMPTY, messageObject.getDocument(), chatActivity.getResourceProvider());
        layout.titleTextView.setText(stickerSet.set.title);
        layout.subtitleTextView.setText(LocaleController.getString("PremiumStickerTooltip", R.string.PremiumStickerTooltip));

        Bulletin.UndoButton viewButton = new Bulletin.UndoButton(chatActivity.getParentActivity(), true, chatActivity.getResourceProvider());
        layout.setButton(viewButton);
        viewButton.setUndoAction(() -> {
            StickersAlert alert = new StickersAlert(chatActivity.getParentActivity(), chatActivity, messageObject.getInputStickerSet(), null, chatActivity.chatActivityEnterView, chatActivity.getResourceProvider());
            alert.setCalcMandatoryInsets(chatActivity.isKeyboardVisible());
            chatActivity.showDialog(alert);
        });
        viewButton.setText(LocaleController.getString("ViewAction", R.string.ViewAction));
        Bulletin bulletin = Bulletin.make(chatActivity, layout, Bulletin.DURATION_LONG);
        bulletin.hash = messageObject.getId();
        bulletin.show();
    }

    public static String unwrapEmoji(String emoji) {
        CharSequence fixedEmoji = emoji;
        int length = emoji.length();
        for (int a = 0; a < length; a++) {
            if (a < length - 1 && (fixedEmoji.charAt(a) == 0xD83C && fixedEmoji.charAt(a + 1) >= 0xDFFB && fixedEmoji.charAt(a + 1) <= 0xDFFF || fixedEmoji.charAt(a) == 0x200D && (fixedEmoji.charAt(a + 1) == 0x2640 || fixedEmoji.charAt(a + 1) == 0x2642))) {
                fixedEmoji = TextUtils.concat(fixedEmoji.subSequence(0, a), fixedEmoji.subSequence(a + 2, fixedEmoji.length()));
                length -= 2;
                a--;
            } else if (fixedEmoji.charAt(a) == 0xfe0f) {
                fixedEmoji = TextUtils.concat(fixedEmoji.subSequence(0, a), fixedEmoji.subSequence(a + 1, fixedEmoji.length()));
                length--;
                a--;
            }
        }
        return fixedEmoji.toString();
    }

    private void sendCurrentTaps() {
        if (lastTappedMsgId == 0) {
            return;
        }
        TLRPC.TL_sendMessageEmojiInteraction interaction = new TLRPC.TL_sendMessageEmojiInteraction();
        interaction.msg_id = lastTappedMsgId;
        interaction.emoticon = lastTappedEmoji;
        interaction.interaction = new TLRPC.TL_dataJSON();
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("v", ANIMATION_JSON_VERSION);
            JSONArray array = new JSONArray();

            for (int i = 0; i < timeIntervals.size(); i++) {
                JSONObject action = new JSONObject();
                action.put("i", animationIndexes.get(i) + 1);
                action.put("t", timeIntervals.get(i) / 1000f);
                array.put(i, action);
            }

            jsonObject.put("a", array);
        } catch (JSONException e) {
            clearSendingInfo();
            FileLog.e(e);
            return;
        }
        interaction.interaction.data = jsonObject.toString();

        TLRPC.TL_messages_setTyping req = new TLRPC.TL_messages_setTyping();
        if (threadMsgId != 0) {
            req.top_msg_id = (int) threadMsgId;
            req.flags |= 1;
        }
        req.action = interaction;
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
        clearSendingInfo();
    }

    private void clearSendingInfo() {
        lastTappedMsgId = 0;
        lastTappedEmoji = null;
        lastTappedTime = 0;
        timeIntervals.clear();
        animationIndexes.clear();
    }

    public void onScrolled(int dy) {
        for (int i = 0; i < drawingObjects.size(); i++) {
            if (!drawingObjects.get(i).viewFound) {
                drawingObjects.get(i).lastY -= dy;
            }
        }
    }

    public boolean isIdle() {
        return drawingObjects.isEmpty();
    }

    public boolean checkPosition(ChatMessageCell messageCell, float chatListViewPaddingTop, int bottom) {
        float y = messageCell.getY() + messageCell.getPhotoImage().getCenterY();
        if (y > chatListViewPaddingTop && y < bottom) {
            return true;
        }
        return false;
    }

    public void cancelAllAnimations() {
        for (int i = 0; i < drawingObjects.size(); i++) {
            drawingObjects.get(i).removing = true;
        }
    }

    public boolean showAnimationForWidget(StoryReactionWidgetView widgetView) {
        if (drawingObjects.size() > 12) {
            return false;
        }
        ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTLReaction(widgetView.mediaArea.reaction);
        String emoji = visibleReaction.emojicon;
        if (emoji == null) {
            TLRPC.Document document = AnimatedEmojiDrawable.findDocument(currentAccount, visibleReaction.documentId);
            emoji = MessageObject.findAnimatedEmojiEmoticon(document);
        }
        MessageObject messageObject = null;


        float imageH = widgetView.getMeasuredHeight();
        float imageW = widgetView.getMeasuredWidth();
        View parent = (View) widgetView.getParent();
        if (imageW > parent.getWidth() * 0.5f) {
            imageH = imageW = parent.getWidth() * 0.4f;
        }
//        if (imageH <= 0 || imageW <= 0) {
//            return false;
//        }

        emoji = unwrapEmoji(emoji);

        int viewId = widgetView.hashCode();
        TLRPC.Document viewDocument = null;
        boolean isOutOwner = widgetView.getTranslationX() > contentLayout.getMeasuredWidth() / 2f;//view.getMessageObject().isOutOwner();
        if (visibleReaction.emojicon != null && createDrawingObject(emoji, viewId, viewDocument, messageObject, -1, false, false, imageW, imageH, isOutOwner)) {
            if (!drawingObjects.isEmpty()) {
                DrawingObject drawingObject = drawingObjects.get(drawingObjects.size() - 1);
                drawingObject.isReaction = true;
                drawingObject.lastH = imageH;
                drawingObject.lastW = imageW;
                drawingObject.lastX = widgetView.getTranslationX() - drawingObject.lastW / 2f;
                drawingObject.lastY = widgetView.getTranslationY() - drawingObject.lastW * 1.5f;
                if (drawingObject.isOut) {
                    drawingObject.lastX += -drawingObject.lastW * 1.8f;
                } else {
                    drawingObject.lastX += -drawingObject.lastW * 0.2f;
                }
            }
            return true;
        } else if (visibleReaction.documentId !=  0 && widgetView.getAnimatedEmojiDrawable() != null) {
            int sameAnimationCount = 0;
            for (int i = 0; i < drawingObjects.size(); i++) {
                if (drawingObjects.get(i).documentId == visibleReaction.documentId) {
                    sameAnimationCount++;
                }
            }
            if (sameAnimationCount >= 4) {
                return false;
            }
            DrawingObject drawingObject = new DrawingObject();
            drawingObject.genericEffect = AnimatedEmojiEffect.createFrom(widgetView.getAnimatedEmojiDrawable(), true, true);
            drawingObject.randomOffsetX = imageW / 4 * ((random.nextInt() % 101) / 100f);
            drawingObject.randomOffsetY = imageH / 4 * ((random.nextInt() % 101) / 100f);
            drawingObject.messageId = viewId;
            drawingObject.document = null;
            drawingObject.documentId = visibleReaction.documentId;
            drawingObject.isOut = isOutOwner;
            drawingObject.isReaction = true;
            drawingObject.lastH = imageH;
            drawingObject.lastW = imageW;
            drawingObject.lastX = widgetView.getTranslationX() - drawingObject.lastW / 2f;
            drawingObject.lastY = widgetView.getTranslationY() - drawingObject.lastW * 1.5f;
            drawingObject.lastX += -drawingObject.lastW * 1.8f;
            if (attached) {
                drawingObject.genericEffect.setView(contentLayout);
            }
            drawingObjects.add(drawingObject);
            return true;
        }
        return false;
    }

    public void setAccount(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public void preload(ReactionsLayoutInBubble.VisibleReaction visibleReaction) {
        String emoji = visibleReaction.emojicon;
        if (emoji == null) {
            TLRPC.Document document = AnimatedEmojiDrawable.findDocument(currentAccount, visibleReaction.documentId);
            emoji = MessageObject.findAnimatedEmojiEmoticon(document);
        }
        if (emoji == null) {
            return;
        }

        ArrayList<TLRPC.Document> arrayList = emojiInteractionsStickersMap.get(emoji);
        if (arrayList == null || arrayList.isEmpty()) {
            return;
        }
        int preloadCount = Math.min(1, arrayList.size());
        for (int i = 0; i < preloadCount; ++i) {
            this.preloadAnimation(arrayList.get(i));
        }
    }

    private class DrawingObject {
        public float lastX;
        public float lastY;
        public boolean viewFound;
        public float lastW;
        public float lastH;
        public float randomOffsetX;
        public float randomOffsetY;
        public boolean isPremiumSticker;
        public boolean isReaction;
        public AnimatedEmojiEffect genericEffect;
        public long documentId;
        boolean wasPlayed;
        boolean isOut;
        boolean removing;
        float removeProgress;
        int messageId;
        TLRPC.Document document;
        ImageReceiver imageReceiver = new ImageReceiver();

        DrawingObject() {
            imageReceiver.setAllowLoadingOnAttachedOnly(true);
            imageReceiver.setAllowDrawWhileCacheGenerating(true);
        }
    }

}
