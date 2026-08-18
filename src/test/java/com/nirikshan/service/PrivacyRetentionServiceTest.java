package com.nirikshan.service;

import com.nirikshan.repository.AuditEventRepository;
import com.nirikshan.repository.RiskEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivacyRetentionServiceTest {
    @Test
    void cleanupDeletesJobUploadsAndPrivacyIntermediateFilesButNotLiveSource(@TempDir Path root) throws Exception {
        Path jobUpload = root.resolve("uploads/42/raw.mp4");
        Path liveSource = root.resolve("uploads/feeds/zone-1/live.mp4");
        Path rawAnnotation = root.resolve("outputs/42/annotated_raw.mp4");
        Files.createDirectories(jobUpload.getParent()); Files.createDirectories(liveSource.getParent()); Files.createDirectories(rawAnnotation.getParent());
        Files.writeString(jobUpload, "raw"); Files.writeString(liveSource, "active"); Files.writeString(rawAnnotation, "intermediate");
        PrivacyRetentionService retention = new PrivacyRetentionService(mock(RiskEventRepository.class), mock(AuditEventRepository.class), new PrivacyAuditService(mock(AuditEventRepository.class), null), root.toString(), 0, 365);

        retention.cleanup();

        assertFalse(Files.exists(jobUpload));
        assertFalse(Files.exists(rawAnnotation));
        assertTrue(Files.exists(liveSource));
    }
}
