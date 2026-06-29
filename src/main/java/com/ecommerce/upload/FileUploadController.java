package com.ecommerce.upload;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Phase 10 — File Upload Controller
 *
 * Two flows:
 *   1. Upload standalone → get back a URL → use that URL in create/update product
 *   2. Upload directly for a product → updates imageUrl on the product in one step
 *
 * @PreAuthorize("hasRole('ADMIN')"):
 *   Only admins can upload product images. Anyone could spam storage otherwise.
 *
 * Content-Type: multipart/form-data
 *   Regular JSON requests send data as text.
 *   File uploads use multipart — the file bytes are sent alongside form fields.
 *   Postman: Body → form-data → key=file, type=File → select your image.
 */
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final ProductService productService;

    /**
     * POST /api/upload/image
     * Body: multipart/form-data, field name = "file"
     * Returns: { data: { url: "/products/uuid.jpg" } }
     *
     * Usage: Upload first, get the URL, then use it in POST /api/products as imageUrl.
     */
    @PostMapping("/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            @RequestParam("file") MultipartFile file) {

        String url = fileStorageService.storeFile(file, "products");
        return ResponseEntity.ok(ApiResponse.success("Image uploaded",
                Map.of("url", url, "filename", file.getOriginalFilename())));
    }

    /**
     * POST /api/upload/product/{id}/image
     * Uploads image AND updates the product's imageUrl in one step.
     */
    @PostMapping("/product/{productId}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadProductImage(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file) {

        String url = fileStorageService.storeFile(file, "products");
        productService.updateImageUrl(productId, url);

        return ResponseEntity.ok(ApiResponse.success("Product image updated",
                Map.of("url", url, "productId", productId.toString())));
    }
}
