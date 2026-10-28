package cl.duoc.foro.forum.controller;

import java.net.URI;
import cl.duoc.foro.forum.dto.ForumDtos.*;
import cl.duoc.foro.forum.service.ForumService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/topics/{topicId}/comments")
@RequiredArgsConstructor
@Validated
public class CommentController {
    private final ForumService service;

    @GetMapping
    public Page<CommentResponse> list(@PathVariable Long topicId,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.comments(topicId, PageRequest.of(page, size, Sort.by("createdAt", "id")));
    }

    @GetMapping("/{id}")
    public CommentResponse get(@PathVariable Long topicId, @PathVariable Long id) {
        return service.comment(topicId, id);
    }

    @PostMapping
    public ResponseEntity<CommentResponse> create(@PathVariable Long topicId,
            @Valid @RequestBody CommentRequest request, @AuthenticationPrincipal Jwt jwt) {
        CommentResponse comment = service.createComment(topicId, request, jwt);
        return ResponseEntity.created(URI.create("/api/topics/" + topicId + "/comments/" + comment.id())).body(comment);
    }

    @PutMapping("/{id}")
    public CommentResponse update(@PathVariable Long topicId, @PathVariable Long id,
            @Valid @RequestBody CommentRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.updateComment(topicId, id, request, jwt);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long topicId, @PathVariable Long id,
                                       @AuthenticationPrincipal Jwt jwt) {
        service.deleteComment(topicId, id, jwt);
        return ResponseEntity.noContent().build();
    }
}