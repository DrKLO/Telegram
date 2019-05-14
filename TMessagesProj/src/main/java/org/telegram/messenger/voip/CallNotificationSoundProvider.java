package org.telegram.messenger.voip;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.ApplicationLoader;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * This is a very dirty hack to allow Telegram calls to respect user's DND settings.
 * URIs to this content provider are specified as the sound for the incoming call notifications.
 * We then assume that the system will only try opening these if it actually wants to make a sound
 * for this particular call - that's how we know whether the call went through DND and start
 * ringing for it. To avoid any potential issues, this serves a wav file containing 5 samples
 * of silence.
 */
public class CallNotificationSoundProvider extends ContentProvider{

	@Override
	public boolean onCreate(){
		return true;
	}

	@Nullable
	@Override
	public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder){
		return null;
	}

	
	@Nullable
	@Override
	public String getType(@NonNull Uri uri){
		return null;
	}

	
	@Nullable
	@Override
	public Uri insert(@NonNull Uri uri, @Nullable ContentValues values){
		return null;
	}

	@Override
	public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs){
		return 0;
	}

	@Override
	public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs){
		return 0;
	}

	@Nullable
	@Override
	public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException{
		if(!"r".equals(mode))
			throw new SecurityException("Unexpected file mode "+mode);
		if(ApplicationLoader.applicationContext==null)
			throw new FileNotFoundException("Unexpected application state");

		VoIPBaseService srv=VoIPBaseService.getSharedInstance();
		if(srv!=null){
			srv.startRingtoneAndVibration();
		}

		try{
			ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
			ParcelFileDescriptor.AutoCloseOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
			byte[] silentWav={82,73,70,70,41,0,0,0,87,65,86,69,102,109,116,32,16,0,0,0,1,0,1,0,68,(byte)172,0,0,16,(byte)177,2,0,2,0,16,0,100,97,116,97,10,0,0,0,0,0,0,0,0,0,0,0,0,0};
			outputStream.write(silentWav);
			outputStream.close();
			return pipe[0];
		}catch(IOException x){
			throw new FileNotFoundException(x.getMessage());
		}
	}
}
