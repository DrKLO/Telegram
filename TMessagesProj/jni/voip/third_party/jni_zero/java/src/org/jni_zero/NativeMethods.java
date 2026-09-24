// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.jni_zero;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface NativeMethods {
    /**
     * Tells the build system to call a different GEN_JNI, prefixed by the value we put here. This
     * should only be used for feature modules where we need a different GEN_JNI. For example, if
     * you did @NativeMethods("dfmname"), this would call into dfmname_GEN_JNI.java.
     */
    public String value() default "";
}
