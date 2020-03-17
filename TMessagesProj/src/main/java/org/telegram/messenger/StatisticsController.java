package org.telegram.messenger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatisticsController extends BaseController
{

    private static final Pattern emojiPattern = Pattern.compile("([\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee])");

    public static class DailyStatistics
    {
        public int chars;
        public int messages;
        public int emojis;
        public int stickers;

        public String format()
        {
            return String.format("Messages  ->  %d\nSymbols     ->  %d\nEmojis        ->  %d\nStickers      ->  %d\n", messages,chars,emojis,stickers);
        }
    }

    public StatisticsController(int num) {
        super(num);
    }

    private static volatile StatisticsController[] Instance = new StatisticsController[UserConfig.MAX_ACCOUNT_COUNT];
    public static StatisticsController getInstance(int num) {
        StatisticsController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (StatisticsController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new StatisticsController(num);
                }
            }
        }
        return localInstance;
    }

    public static String getCurrentDate()
    {
        return new SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(new Date()) ;
    }

    private int emojisCount(String message)
    {
        int count = 0 ;
        Matcher matcher = emojiPattern.matcher(message);
        while (matcher.find())
            count++;
        return count;
    }

    public void updateStatistics(String message)
    {
        String date = getCurrentDate();
        try {
            DailyStatistics cur = getMessagesStorage().getDailyStatistics(date);
            cur.messages += 1;
            if (message == null)
                cur.stickers += 1;
            else
            {
                cur.chars += message.length();
                cur.emojis += emojisCount(message);
            }
            getMessagesStorage().setDailyStatistics(cur,date);
        } catch (Exception e) {
            FileLog.e(e);
        }

    }




}
