package org.telegram.messenger;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

public class ChatMessageSharedResources {

    public Context context;
    public Drawable chat_msgAvatarLiveLocationDrawable;
    public Drawable chat_redLocationIcon;

    public ChatMessageSharedResources(Context context) {
        this.context = context;
    }

    public Drawable getRedLocationIcon() {
        if (chat_redLocationIcon == null) {
            Resources resources = context.getResources();
            chat_redLocationIcon = resources.getDrawable(R.drawable.map_pin).mutate();
        }
        return chat_redLocationIcon;
    }

    public Drawable getAvatarLiveLocation() {
        if (chat_msgAvatarLiveLocationDrawable == null) {
            Resources resources = context.getResources();
            chat_msgAvatarLiveLocationDrawable = resources.getDrawable(R.drawable.livepin).mutate();
        }
        return chat_msgAvatarLiveLocationDrawable;
    }
}
