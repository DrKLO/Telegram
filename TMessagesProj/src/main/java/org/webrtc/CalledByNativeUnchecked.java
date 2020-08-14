/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *  @CalledByNativeUnchecked is used to generate JNI bindings that do not check for exceptions.
 *  It only makes sense to use this annotation on methods that declare a throws... spec.
 *  However, note that the exception received native side maybe an 'unchecked' (RuntimeExpception)
 *  such as NullPointerException, so the native code should differentiate these cases.
 *  Usage of this should be very rare; where possible handle exceptions in the Java side and use a
 *  return value to indicate success / failure.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface CalledByNativeUnchecked {
  /*
   *  If present, tells which inner class the method belongs to.
   */
  public String value() default "";
}
