// Copyright 2012 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jni_zero;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used by the JNI generator to create the necessary JNI bindings and expose this method to native
 * code.
 *
 * <p>Any uncaught Java exceptions will crash the current process. This is generally the desired
 * behavior, since most exceptions indicate an unexpected error. If your java method expects an
 * exception, we recommend refactoring to catch exceptions and indicate errors with special return
 * values instead. If this is not possible, see {@link CalledByNativeUnchecked} instead.
 */
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface CalledByNative {
    /*
     *  If present, tells which inner class the method belongs to.
     */
    public String value() default "";
}
