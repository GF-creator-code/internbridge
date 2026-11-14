package com.getjobs.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumeSkillsTest {
    private final AiService ai = mock(AiService.class);
    private final ResumeSkillsService service = new ResumeSkillsService(ai, new ObjectMapper());

    @Test void extractsGroundedSkillsFromWordDocument() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("""
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body><w:p><w:r><w:t>用 SQL </w:t></w:r><w:r><w:t>分析交易数据。</w:t></w:r></w:p></w:body></w:document>
                    """.getBytes(StandardCharsets.UTF_8));
        }
        when(ai.sendRequest(contains("用 SQL 分析交易数据。"))).thenReturn("""
                {"skills":[{"name":"SQL","evidence":"用 SQL 分析交易数据。"}]}
                """);
        var file = new MockMultipartFile("file","resume.docx","application/octet-stream",bytes.toByteArray());
        assertEquals("SQL",service.extract(file).getFirst().name());
        verify(ai, never()).saveOrUpdateAiConfig(anyString(), anyString());
    }

    @Test void rejectsUnsupportedFileBeforeContactingModel() {
        var file = new MockMultipartFile("file","resume.png","image/png",new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> service.extract(file));
        verifyNoInteractions(ai);
    }

    @Test void readsTextPdfAndExplainsAnImageOnlyPage() throws Exception {
        try (var doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            var bytes = new ByteArrayOutputStream();
            doc.save(bytes);
            var blank = new MockMultipartFile("file", "scan.pdf", "application/pdf", bytes.toByteArray());
            assertTrue(assertThrows(IllegalArgumentException.class, () -> service.extract(blank)).getMessage().contains("OCR"));
            verifyNoInteractions(ai);
            try (var content = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Built SQL reports");
                content.endText();
            }
            bytes.reset();
            doc.save(bytes);
            var pdf = new MockMultipartFile("file", "intern.pdf", "application/pdf", bytes.toByteArray());
            when(ai.sendRequest(contains("Built SQL reports"))).thenReturn(
                    "{\"skills\":[{\"name\":\"SQL\",\"evidence\":\"Built SQL reports\"}]}");
            assertEquals("Built SQL reports", service.extract(pdf).getFirst().evidence());
        }
    }

    @Test void rejectsSkillEvidenceAbsentFromResume() {
        when(ai.sendRequest(anyString())).thenReturn("""
                {"skills":[{"name":"Python","evidence":"Wrote Python scripts"}]}
                """);
        var file = new MockMultipartFile("file","resume.txt","text/plain","Built SQL reports".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> service.extract(file));
    }

    @Test void comparesJobWithProfileAndKeepsUnprovenRequirementsSeparate() {
        when(ai.sendRequest(contains("SQL reports"))).thenReturn("""
                {"matches":[{"requirement":"SQL","evidence":"SQL reports"}],"gaps":["Cantonese"]}
                """);
        var result = service.match("SQL reports","SQL and Cantonese");
        assertEquals("SQL reports",result.matches().getFirst().evidence());
        assertEquals("Cantonese",result.gaps().getFirst());
        assertThrows(IllegalArgumentException.class, () -> service.match("SQL","  "));
        when(ai.sendRequest(anyString())).thenReturn("""
                {"matches":[{"requirement":"Cantonese","evidence":"native speaker"}],"gaps":[]}
                """);
        assertThrows(IllegalStateException.class, () -> service.match("SQL reports","Cantonese"));
    }
}
