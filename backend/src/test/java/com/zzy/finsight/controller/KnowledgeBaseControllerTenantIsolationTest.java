package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseControllerTenantIsolationTest {

    @Test
    void uploadAndClearUseCurrentUserKnowledgeSpace() {
        RagService ragService = mock(RagService.class);
        UserContext userContext = mock(UserContext.class);
        when(userContext.currentUserId()).thenReturn(7L);
        UploadController uploadController = new UploadController(ragService, userContext);
        ClearController clearController = new ClearController(ragService, userContext);
        MockMultipartFile file = new MockMultipartFile(
                "files", "report.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8)
        );

        uploadController.upload(List.of(file));
        clearController.clear();

        verify(ragService).process(7L, List.of(file));
        verify(ragService).clear(7L);
    }
}
