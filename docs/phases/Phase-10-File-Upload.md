# Phase 10 — File Upload (Product Images)

## Objective
Accept image uploads from admins, store them on disk (learning) with the architecture ready to swap to S3 (production), and serve them via HTTP.

---

## What We Built
| File | Purpose |
|---|---|
| `upload/FileStorageService.java` | File validation, UUID naming, disk storage |
| `upload/FileUploadController.java` | Upload endpoints (admin only) |
| `config/WebConfig.java` | Serve uploaded files as static resources |
| `application.properties` | `file.upload.dir`, multipart size limits |

## API Endpoints Built
```
POST /api/upload/image
  Body: multipart/form-data, field: file (image file)
  Returns: { url: "/products/uuid.jpg" }
  Auth: ADMIN only

POST /api/upload/product/{id}/image
  Uploads image AND updates product.imageUrl in one step
  Auth: ADMIN only

GET /api/files/products/{filename}
  Serves the uploaded image file
  Public (no auth needed — images are public)
```

### Postman Usage
```
Method: POST
URL: http://localhost:8080/api/upload/image
Authorization: Bearer <admin-token>
Body: form-data
  Key: file  (change type from Text to File)
  Value: [select your image file]
```

---

## Concepts Introduced

### Why NOT Store Images in the Database?

```
Tempting approach: BYTEA column in PostgreSQL
  @Column(columnDefinition = "BYTEA")
  private byte[] imageData;

Problems:
  1. DB row size: products table goes from 1KB to 5MB per row
  2. Backup size explodes: pg_dump includes all binary data
  3. Every product query carries image bytes in memory
  4. DB is optimized for structured data — not binary blobs
  5. Can't serve files via CDN (must go through app server)

Correct approach: store file on disk/S3, store URL in DB
  Product.imageUrl = "/products/uuid.jpg"  (just a string, ~40 bytes)
  File lives on disk (local) or S3 (production)
  Client fetches image directly from storage — app server not involved
```

### Local Storage → S3: Same Interface, Different Impl

```
Learning (our implementation):
  Files.copy(file.getInputStream(), targetPath); // write to disk
  return "/products/uuid.jpg";

Production (S3 swap — same method signature):
  s3Client.putObject(
      PutObjectRequest.builder().bucket("my-bucket").key("products/" + filename).build(),
      RequestBody.fromInputStream(file.getInputStream(), file.getSize())
  );
  return "https://my-bucket.s3.ap-south-1.amazonaws.com/products/" + filename;

The FileStorageService interface doesn't change.
Controllers don't change.
Only storeFile() body changes.
This is the STRATEGY pattern — swappable implementation.
```

### Security: Why UUID Filenames?

```
Attack: path traversal
  Attacker uploads file named: "../../etc/passwd"
  Without UUID: we'd try to write to /app/../../etc/passwd → /etc/passwd
  With UUID: filename becomes "a3f7c2b1-...jpg" → safe

Attack: filename collision
  Two users upload "product.jpg" → second overwrites first
  UUID is statistically unique → no collisions

Attack: executable upload
  Attacker uploads "shell.php" or "backdoor.exe"
  Our MIME type check rejects non-images
  Only: image/jpeg, image/png, image/webp, image/gif allowed

Validation in storeFile():
  1. File not empty check
  2. MIME type whitelist check
  3. UUID filename (prevents traversal + collision)
  4. Size limit from application.properties (5MB max)
```

### `MultipartFile` — Spring's File Abstraction

```java
@RequestParam("file") MultipartFile file

// What MultipartFile gives you:
file.getOriginalFilename()  // user's original filename ("photo.jpg")
file.getContentType()       // MIME type ("image/jpeg")
file.getSize()              // file size in bytes
file.getInputStream()       // raw bytes stream to read/copy
file.isEmpty()              // true if no file was sent
```

HTTP sends files as `multipart/form-data`:
  Each part = a form field or file
  File part = headers (filename, content-type) + body (raw bytes)
  Spring parses multipart and gives you `MultipartFile`

### Static Resource Serving — `WebMvcConfigurer`

```java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/files/**")
            .addResourceLocations("file:/path/to/uploads/");
}
```

Without this:
  `GET /api/files/products/uuid.jpg` → 404 (Spring doesn't know about the upload dir)

With this:
  Spring maps `/files/**` → files on disk at `upload.dir`
  `GET /api/files/products/uuid.jpg` → Spring reads the file → returns bytes → 200

In production, Nginx does this:
  `location /files/ { root /var/uploads; }` → serves files directly, bypasses Java

### The Two-Step vs One-Step Upload Flow

```
Two-step (more flexible):
  Step 1: POST /api/upload/image → get URL "/products/uuid.jpg"
  Step 2: POST /api/products { ..., "imageUrl": "/products/uuid.jpg" }
  Use case: user picks image first, then fills product details
  OR: reuse the same image for multiple products

One-step (more convenient):
  POST /api/upload/product/5/image → uploads + updates product.imageUrl in one call
  Use case: admin editing an existing product's image
```

### Spring Multipart Configuration

```properties
spring.servlet.multipart.enabled=true         # enable multipart parsing
spring.servlet.multipart.max-file-size=5MB    # per-file limit
spring.servlet.multipart.max-request-size=10MB # total request limit

# These are enforced by Spring before your controller runs
# Exceeded → MaxUploadSizeExceededException → 413 Payload Too Large
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| 403 on upload | Not ADMIN or no token | Use ADMIN token |
| `MaxUploadSizeExceededException` | File > 5MB | Raise limit in properties or compress image |
| Uploaded file not served (404) | `WebConfig` not added, or context-path issue | Check `/files/**` is in `addResourceHandlers` |
| `IllegalArgumentException: File type not allowed` | Non-image MIME type | Only JPEG, PNG, WebP, GIF accepted |
| Image URL is null after upload | Forgot to call `updateImageUrl()` | Use the one-step upload endpoint for products |
| Upload dir not created | No write permission on the dir | App creates it via `Files.createDirectories()` — check OS permissions |

---

## Interview Questions

**Q: Why should images not be stored directly in the database?**
> Databases are optimized for structured, indexed data — not binary blobs. Storing 5MB images in a `BYTEA` column inflates row size, bloats backups, loads image bytes into memory on every product query, and prevents CDN caching. The correct pattern: store files on disk/S3, store only the URL string in the DB column. Images are then served directly by a file server or CDN — the app server is never involved in file delivery.

**Q: What security risks exist with file uploads? How do you mitigate them?**
> Three main risks: (1) **Path traversal** — filename `../../etc/passwd` could overwrite system files. Mitigation: use UUID filenames, never use user-supplied names. (2) **Malicious file type** — user uploads PHP/shell script disguised as image. Mitigation: validate MIME type against whitelist of allowed image types. (3) **Denial of service** — huge files exhaust disk/memory. Mitigation: size limits in `max-file-size`.

**Q: How would you migrate from local file storage to S3?**
> The `FileStorageService` is the abstraction boundary. Extract an interface (`StorageService`) with `storeFile()` and `deleteFile()`. Create `LocalStorageService` (current code) and `S3StorageService` (uses AWS SDK). Swap the `@Primary` bean in config. Controllers and callers change nothing — they only know the interface.

**Q: What is `MultipartFile` in Spring?**
> It's Spring's abstraction for a file received in a multipart HTTP request. It provides the original filename, MIME content type, file size, and an `InputStream` to read the bytes. Spring automatically parses the `multipart/form-data` request and injects `MultipartFile` parameters.

**Q: What is a CDN and when would you add one?**
> CDN (Content Delivery Network) — a globally distributed network of edge servers. When a user in Chennai requests `/files/products/uuid.jpg`, the CDN serves it from the nearest server (e.g., Mumbai) instead of your origin server in US East. Result: low latency for users everywhere, less load on your app. Add one when: your user base is geographically distributed, you have lots of static assets, or page load speed is critical for conversion.

---

## MFAQ

**Can I store the absolute path in the DB instead of the relative URL?**
Never. Absolute paths are environment-specific. If you move the app to a different server, all stored paths break. Relative URL (`/products/uuid.jpg`) works everywhere — the base URL changes but the relative path doesn't.

**What is MinIO?**
MinIO is an S3-compatible object storage you can run locally (Docker: `docker run -p 9000:9000 minio/minio`). Perfect for learning S3 APIs without an AWS account. The AWS SDK works against MinIO with just a different endpoint URL.

**Why is `/api/files/**` public even though uploads require ADMIN?**
Uploading = write operation = admin only. Viewing an uploaded image = read operation = public. Just like: only admins can create products, but anyone can view them. The image itself is public content once uploaded.
