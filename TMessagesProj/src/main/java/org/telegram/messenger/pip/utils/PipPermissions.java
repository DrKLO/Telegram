package org.telegram.messenger.pip.utils;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
        PipPermissions.PIP_DENIED_PIP,
        PipPermissions.PIP_DENIED_OVERLAY,
        PipPermissions.PIP_GRANTED_PIP,
        PipPermissions.PIP_GRANTED_OVERLAY,
})
public @interface PipPermissions {
    int PIP_DENIED_PIP = -2;
    int PIP_DENIED_OVERLAY = -1;
    int PIP_GRANTED_PIP = 1;
    int PIP_GRANTED_OVERLAY = 2;
}
