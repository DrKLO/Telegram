package org.telegram.messenger.voip;

import java.nio.ByteBuffer;

/**
 * Created by grishka on 01.04.17.
 */

public class Resampler {
	public static native int convert44to48(ByteBuffer from, ByteBuffer to);
	public static native int convert48to44(ByteBuffer from, ByteBuffer to);
}
