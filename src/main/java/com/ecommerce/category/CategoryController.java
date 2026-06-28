package com.ecommerce.category;

import com.ecommerce.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Category>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Categories fetched",
                categoryService.getAllActive()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Category>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Category fetched",
                categoryService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Category>> create(@RequestBody Map<String, String> body) {
        Category created = categoryService.create(body.get("name"), body.get("description"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created", created));
    }
}
