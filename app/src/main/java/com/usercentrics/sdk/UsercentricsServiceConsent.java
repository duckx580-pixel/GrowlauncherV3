package com.usercentrics.sdk;

import com.usercentrics.sdk.models.settings.UsercentricsConsentType;
import java.util.List;

public final class UsercentricsServiceConsent {
    private final String templateId;
    private final boolean status;
    private final List<UsercentricsConsentHistoryEntry> history;
    private final UsercentricsConsentType type;
    private final String dataProcessor;
    private final String version;
    private final boolean isEssential;
    private final String category;

    public UsercentricsServiceConsent(String templateId, boolean status,
            List<UsercentricsConsentHistoryEntry> history, UsercentricsConsentType type,
            String dataProcessor, String version, boolean isEssential, String category) {
        this.templateId = templateId;
        this.status = status;
        this.history = history;
        this.type = type;
        this.dataProcessor = dataProcessor;
        this.version = version;
        this.isEssential = isEssential;
        this.category = category;
    }

    public final String getTemplateId() { return templateId; }
    public final boolean getStatus() { return status; }
    public final List<UsercentricsConsentHistoryEntry> getHistory() { return history; }
    public final UsercentricsConsentType getType() { return type; }
    public final String getDataProcessor() { return dataProcessor; }
    public final String getVersion() { return version; }
    public final boolean isEssential() { return isEssential; }
    public final String getCategory() { return category; }
}
