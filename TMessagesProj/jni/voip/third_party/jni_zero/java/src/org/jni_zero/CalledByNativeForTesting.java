// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jni_zero;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used by the JNI generator to create the necessary JNI bindings and expose this method to native
 * test-only code.
 *
 * <p>Any method annotated by this will be kept around for tests only. If you wish to call your
 * method from non-test code, see {@link CalledByNative} instead.
 */
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface CalledByNativeForTesting {
    /*
     *  If present, tells which inner class the method belongs to.
     */
    public String value() default "";
}
