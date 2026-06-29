package com.ecommerce.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * WHY FILE UPLOAD IS SEPARATE FROM PRODUCT?
 *
 * Option 1 (bad): Store image as BLOB in PostgreSQL
 *   - DB row size explodes — binary data doesn't belong in relational DBs
 *   - DB backups become huge
 *   - Every product query carries image bytes in memory
 *
 * Option 2 (good): Store file on disk, store URL in DB
 *   - DB stores only a string path/URL (tiny)
 *   - Files served by Nginx/CDN directly (no app server involved)
 *   - Standard industry pattern
 *
 * Our local storage is a learning implementation.
 * Production: replace storeFile() with S3 SDK call → same interface, different impl.
 *
 *
 * S3 production equivalent:
 *   s3Client.putObject(PutObjectRequest.builder()
 *       .bucket("ecommerce-images")
 *       .key("products/" + filename)
 *       .build(), RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
 *   return "https://ecommerce-images.s3.amazonaws.com/products/" + filename;
 */
@Service
@Slf4j
public class FileStorageService {

    // Allowed MIME types — security: don't allow executables to be uploaded
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );

    private final Path uploadDir;

    public FileStorageService(@Value("${file.upload.dir:uploads}") String uploadDirStr) {
        this.uploadDir = Paths.get(uploadDirStr).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
            log.info("File upload directory: {}", this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create upload directory: " + this.uploadDir, e);
        }
    }

    /**
     * Stores the uploaded file on disk and returns the relative URL path.
     *
     * Security checks:
     *   1. MIME type must be an image — reject PDFs, executables, etc.
     *   2. Random UUID filename — prevents path traversal attacks
     *      (if user names file "../../etc/passwd", our UUID replaces it)
     *   3. Max size enforced by spring.servlet.multipart.max-file-size=5MB
     */
    public String storeFile(MultipartFile file, String subfolder) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "File type not allowed. Only images (JPEG, PNG, WebP, GIF) are accepted.");
        }

        // Extract original extension (e.g., ".jpg")
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        // UUID filename — unique, no collision, no path traversal possible
        String storedFilename = UUID.randomUUID() + extension;

        try {
            Path targetDir = uploadDir.resolve(subfolder);
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(storedFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String relativePath = "/" + subfolder + "/" + storedFilename;
            log.info("File stored: {}", relativePath);
            return relativePath;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Path filePath = uploadDir.resolve(relativePath.startsWith("/")
                    ? relativePath.substring(1) : relativePath).normalize();
            Files.deleteIfExists(filePath);
            log.info("File deleted: {}", filePath);
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", relativePath, e.getMessage());
        }
    }
}
