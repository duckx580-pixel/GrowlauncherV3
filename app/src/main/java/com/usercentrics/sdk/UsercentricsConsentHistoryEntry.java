package com.usercentrics.sdk;

import com.usercentrics.sdk.models.settings.UsercentricsConsentType;

public final class UsercentricsConsentHistoryEntry {
    private final boolean status;
    private final UsercentricsConsentType type;
    private final long timestampInMillis;

    public UsercentricsConsentHistoryEntry(boolean status, UsercentricsConsentType type, long timestampInMillis) {
        this.status = status;
        this.type = type;
        this.timestampInMillis = timestampInMillis;
    }

    public final boolean getStatus() { return status; }
    public final UsercentricsConsentType getType() { return type; }
    public final long getTimestampInMillis() { return timestampInMillis; }
}
