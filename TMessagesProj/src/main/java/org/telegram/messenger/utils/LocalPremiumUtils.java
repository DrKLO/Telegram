package org.telegram.messenger.utils;


import android.content.Context;
import org.telegram.messenger.ApplicationLoader;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class LocalPremiumUtils {

    public static Boolean readData(String fileName) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return false;

        try {
            String[] fileList = context.fileList();
            if (fileList != null) {
                for (String file : fileList) {
                    if (file.equals(fileName)) {
                        FileInputStream fileIS = context.openFileInput(fileName);
                        ObjectInputStream objIS = new ObjectInputStream(fileIS);
                        Boolean data = (Boolean) objIS.readObject();
                        objIS.close();
                        fileIS.close();
                        return data;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void saveData(String fileName, Object data) {
        tryWith(() -> {
            Context context = ApplicationLoader.applicationContext;
            if (context != null) {
                FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
                ObjectOutputStream os = new ObjectOutputStream(fos);
                os.writeObject(data);
                os.close();
                fos.close();
            }
            return null;
        });
    }

    public static <T> T tryWith(Callable<T> call) {
        try {
            return call.call();
        } catch (Exception e) {
            return null;
        }
    }

    public interface Callable<T> {
        T call() throws Exception;
    }
}
