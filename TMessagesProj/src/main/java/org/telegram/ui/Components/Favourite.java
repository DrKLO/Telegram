package org.telegram.ui.Components;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

public class Favourite {

    long id;
    long chat_id;

    public Favourite(){

    }

    public Favourite(long id, long chat_id){
        this.id = id;
        this.chat_id = chat_id;
    }

    public Favourite(long chat_id){
        this.chat_id = chat_id;
    }

    public long getChatID(){
        return this.chat_id;
    }

    public long getID(){
        return this.id;
    }

    public void setChatID(long chat_id){
        this.chat_id = chat_id;
    }

    public void setID(long id){
        this.id = id;
    }

    public static void addFavourite(Long id){
        Favourite favourite = new Favourite(id);
        ApplicationLoader.databaseHandler.addFavourite(favourite);
    }

    public static void deleteFavourite(Long id){
        ApplicationLoader.databaseHandler.deleteFavourite(id);
    }

    public static boolean isFavourite(Long id){
        try{
            Favourite favourite = ApplicationLoader.databaseHandler.getFavouriteByChatId(id);
            if(favourite == null){
                return false;
            }else{
                return true;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return false;
        }
    }
}

