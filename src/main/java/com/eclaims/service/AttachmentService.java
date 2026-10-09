package com.eclaims.service;

import com.eclaims.entity.ClaimAttachment;
import com.eclaims.entity.ClaimFormRow;
import com.eclaims.repository.ClaimAttachmentRepository;
import com.eclaims.repository.ClaimFormRowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    private final ClaimAttachmentRepository attachmentRepository;
    private final ClaimFormRowRepository    rowRepository;

    @Value("${eclaims.upload.path:C:/eclaims-uploads/}")
    private String uploadPath;

    private static final List<String> ALLOWED_TYPES = List.of(
        "image/jpeg", "image/png", "image/gif",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    // ── Form-level attachment ─────────────────────────────────
    @Transactional
    public ClaimAttachment uploadFormAttachment(String workflowId, String formId,
                                                 MultipartFile file) throws IOException {
        validateFile(file);
        String stored = saveFile(file, workflowId);

        ClaimAttachment att = ClaimAttachment.builder()
            .workflowId(workflowId).formId(formId)
            .attachment(file.getOriginalFilename())
            .storedFileName(stored)
            .contentType(file.getContentType())
            .status("New")
            .build();
        return attachmentRepository.save(att);
    }

    // ── Row-level attachment ──────────────────────────────────
    @Transactional
    public ClaimFormRow uploadRowAttachment(Integer rowId, MultipartFile file) throws IOException {
        validateFile(file);
        ClaimFormRow row = rowRepository.findById(rowId)
            .orElseThrow(() -> new IllegalArgumentException("Row not found: " + rowId));

        // Delete old file if exists
        if (row.getAttachmentPath() != null) deleteFile(row.getAttachmentPath());

        String stored = saveFile(file, row.getWorkflowId());
        row.setAttachmentPath(stored);
        row.setAttachmentOriginalName(file.getOriginalFilename());
        return rowRepository.save(row);
    }

    // ── Mileage attachment ──────────────────────────────────
    @Transactional
    public ClaimAttachment uploadMileageAttachment(String workflowId, String formId,
                                                   MultipartFile file) throws IOException {
        validateFile(file);

        // Only one mileage attachment per claim: replace the old one
        attachmentRepository.findByWorkflowIdAndCategory(workflowId, "MILEAGE")
                .forEach(old -> {
                    deleteFile(old.getStoredFileName());
                    attachmentRepository.delete(old);
                });

        String stored = saveFile(file, workflowId);

        ClaimAttachment att = ClaimAttachment.builder()
                .workflowId(workflowId).formId(formId)
                .attachment(file.getOriginalFilename())
                .storedFileName(stored)
                .contentType(file.getContentType())
                .category("MILEAGE")
                .status("New")
                .build();
        return attachmentRepository.save(att);
    }

    // ── Delete row attachment ─────────────────────────────────
    @Transactional
    public void deleteRowAttachment(Integer rowId) {
        ClaimFormRow row = rowRepository.findById(rowId)
            .orElseThrow(() -> new IllegalArgumentException("Row not found"));
        if (row.getAttachmentPath() != null) {
            deleteFile(row.getAttachmentPath());
            row.setAttachmentPath(null);
            row.setAttachmentOriginalName(null);
            rowRepository.save(row);
        }
    }

    // ── Delete form attachment ────────────────────────────────
    @Transactional
    public void deleteFormAttachment(Integer attachId) {
        attachmentRepository.findById(attachId).ifPresent(att -> {
            deleteFile(att.getStoredFileName());
            attachmentRepository.delete(att);
        });
    }

    // ── Get file path for download ────────────────────────────
    public Path getFilePath(String storedFileName) {
        return Paths.get(uploadPath, storedFileName);
    }

    public List<ClaimAttachment> getFormAttachments(String workflowId) {
        return attachmentRepository.findByWorkflowIdOrderByDocDate(workflowId);
    }

    // ── Internal helpers ──────────────────────────────────────
    private String saveFile(MultipartFile file, String workflowId) throws IOException {
        Path dir = Paths.get(uploadPath, workflowId);
        Files.createDirectories(dir);
        String ext      = getExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + ext;
        Files.copy(file.getInputStream(), dir.resolve(filename),
                   StandardCopyOption.REPLACE_EXISTING);
        return workflowId + "/" + filename;
    }

    private void deleteFile(String stored) {
        if (stored == null) return;
        try { Files.deleteIfExists(Paths.get(uploadPath, stored)); }
        catch (IOException e) { log.warn("Could not delete file: {}", stored); }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("No file provided");
        if (file.getSize() > 10 * 1024 * 1024)
            throw new IllegalArgumentException("File too large (max 10MB)");
        if (!ALLOWED_TYPES.contains(file.getContentType()))
            throw new IllegalArgumentException("File type not allowed: " + file.getContentType());
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx) : "";
    }
}
