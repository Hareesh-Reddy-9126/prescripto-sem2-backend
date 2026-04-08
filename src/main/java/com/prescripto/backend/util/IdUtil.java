package com.prescripto.backend.util;

import org.bson.types.ObjectId;

public final class IdUtil {

    private IdUtil() {
    }

    public static String objectId() {
        return new ObjectId().toHexString();
    }
}
