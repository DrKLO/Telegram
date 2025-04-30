package org.telegram.messenger.pip;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
        PipNativeApiController.PIP_DENIED_PIP,
        PipNativeApiController.PIP_DENIED_OVERLAY,
        PipNativeApiController.PIP_GRANTED_PIP,
        PipNativeApiController.PIP_GRANTED_OVERLAY,
})
public @interface PipPermissions {
}
