package cl.duoc.foro.forum.controller;

import java.net.URI;
import java.util.List;
import cl.duoc.foro.forum.dto.ForumDtos.*;
import cl.duoc.foro.forum.service.ForumService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final ForumService service;

    @GetMapping
    public List<CategoryResponse> list() {
        return service.categories();
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable Long id) {
        return service.category(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse category = service.createCategory(request);
        return ResponseEntity.created(URI.create("/api/categories/" + category.id())).body(category);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return service.updateCategory(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}