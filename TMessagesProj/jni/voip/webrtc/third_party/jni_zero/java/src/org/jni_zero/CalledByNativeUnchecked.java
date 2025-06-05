// Copyright 2012 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jni_zero;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Similar to {@link CalledByNative}, this also exposes JNI bindings to native code. The main
 * difference is this <b>will not</b> crash the browser process if the Java method throws an
 * exception. However, the C++ caller <b>must</b> handle and clear the exception before calling into
 * any other Java code, otherwise the next Java method call will crash (with the previous call's
 * exception, which leads to a very confusing debugging experience).
 *
 * <p>Usage of this annotation should be very rare; due to the complexity of correctly handling
 * exceptions in C++, prefer using {@link CalledByNative}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface CalledByNativeUnchecked {
    /*
     *  If present, tells which inner class the method belongs to.
     */
    public String value() default "";
}
