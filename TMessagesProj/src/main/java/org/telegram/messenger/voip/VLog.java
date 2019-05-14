package org.telegram.messenger.voip;

import android.text.TextUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

class VLog{
	public native static void v(String msg);
	public native static void d(String msg);
	public native static void i(String msg);
	public native static void w(String msg);
	public native static void e(String msg);

	public static void e(Throwable x){
		e(null, x);
	}

	public static void e(String msg, Throwable x){
		StringWriter sw=new StringWriter();
		if(!TextUtils.isEmpty(msg)){
			sw.append(msg);
			sw.append(": ");
		}
		PrintWriter pw=new PrintWriter(sw);
		x.printStackTrace(pw);
		String[] lines=sw.toString().split("\n");
		for(String line:lines)
			e(line);
	}
}
