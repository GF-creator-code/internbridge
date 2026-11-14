package com.getjobs.application.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.nio.file.Files;
import java.nio.file.Path;

final class ResumeText {
    static final int MAX_CHARS = 50_000;
    private static final int MAX_FILE = 10 * 1024 * 1024;
    private static final int MAX_DOCUMENT_XML = 4 * 1024 * 1024;

    static String read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择有内容的简历。");
        if (file.getSize() > MAX_FILE) throw new IllegalArgumentException("简历文件最大支持 10 MB。");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String text;
        try {
            if (name.endsWith(".pdf")) {
                try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
                    if (pdf.getNumberOfPages() > 60) throw new IllegalArgumentException("简历最多支持 60 页。");
                    if (!pdf.getCurrentAccessPermission().canExtractContent()) throw new IllegalArgumentException("请上传允许提取文字的 PDF。");
                    var stripper = new PDFTextStripper();
                    stripper.setSortByPosition(true);
                    text = stripper.getText(pdf);
                }
            } else if (name.endsWith(".docx")) {
                text = readWord(file);
            } else if (name.endsWith(".txt")) {
                if (file.getSize() > 4L * MAX_CHARS + 3) throw new IllegalArgumentException("简历文本过长，请保留相关经历后重试。");
                text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(file.getBytes())).toString().replaceFirst("^\uFEFF", "");
            } else {
                throw new IllegalArgumentException("请上传 PDF、DOCX 或 UTF-8 TXT 简历。");
            }
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("请解除 PDF 密码保护后上传。");
        } catch (IOException | javax.xml.stream.XMLStreamException e) {
            throw new IllegalArgumentException("无法读取简历，请检查文件格式；TXT 请使用 UTF-8。");
        }
        text = text.strip();
        if (text.isBlank()) throw new IllegalArgumentException("没有可读取的文字。扫描件请先做 OCR，或上传 Word 版。");
        if (text.length() > MAX_CHARS) throw new IllegalArgumentException("简历文本过长，请保留相关经历后重试。");
        return text;
    }

    private static String readWord(MultipartFile file) throws IOException, javax.xml.stream.XMLStreamException {
        // Read only the document body. Never decompress embedded files, images or external relationships.
        Path temp = Files.createTempFile("getjobs-resume-", ".docx");
        try {
            file.transferTo(temp);
            try (ZipFile zip = new ZipFile(temp.toFile())) {
                ZipEntry entry = zip.getEntry("word/document.xml");
                if (entry == null || entry.getSize() > MAX_DOCUMENT_XML) throw new IllegalArgumentException("Word 正文缺失或过大。");
                byte[] body;
                try (var stream = zip.getInputStream(entry)) { body = stream.readNBytes(MAX_DOCUMENT_XML + 1); }
                if (body.length > MAX_DOCUMENT_XML) throw new IllegalArgumentException("Word 正文过大。");
                var factory = XMLInputFactory.newFactory();
                factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
                factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
                XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(body));
                var text = new StringBuilder();
                try {
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.DTD) throw new IllegalArgumentException("Word 正文格式不受支持。");
                        if (event != XMLStreamConstants.START_ELEMENT && event != XMLStreamConstants.END_ELEMENT) continue;
                        String namespace = reader.getNamespaceURI();
                        if (!"http://schemas.openxmlformats.org/wordprocessingml/2006/main".equals(namespace)
                                && !"http://purl.oclc.org/ooxml/wordprocessingml/main".equals(namespace)) continue;
                        String tag = reader.getLocalName();
                        if (event == XMLStreamConstants.START_ELEMENT && tag.equals("t")) text.append(reader.getElementText());
                        if (event == XMLStreamConstants.START_ELEMENT && (tag.equals("tab") || tag.equals("br"))) text.append(' ');
                        if (event == XMLStreamConstants.END_ELEMENT && tag.equals("p")) text.append('\n');
                        if (text.length() > MAX_CHARS) throw new IllegalArgumentException("简历文本过长，请保留相关经历后重试。");
                    }
                } finally { reader.close(); }
                return text.toString();
            }
        } finally { Files.deleteIfExists(temp); }
    }

    private ResumeText() {}
}
