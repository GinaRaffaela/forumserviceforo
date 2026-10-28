package cl.duoc.foro.forum.controller;

import java.net.URI;
import cl.duoc.foro.forum.dto.ForumDtos.*;
import cl.duoc.foro.forum.service.ForumService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/topics")
@RequiredArgsConstructor
@Validated
public class TopicController {
    private final ForumService service;

    @GetMapping
    public Page<TopicResponse> search(@RequestParam(defaultValue = "") @Size(max = 180) String q,
                                      @RequestParam(required = false) Long categoryId,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(q, categoryId, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @GetMapping("/{id}")
    public TopicResponse get(@PathVariable Long id) {
        return service.topic(id);
    }

    @PostMapping
    public ResponseEntity<TopicResponse> create(@Valid @RequestBody TopicRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        TopicResponse topic = service.createTopic(request, jwt);
        return ResponseEntity.created(URI.create("/api/topics/" + topic.id())).body(topic);
    }

    @PutMapping("/{id}")
    public TopicResponse update(@PathVariable Long id, @Valid @RequestBody TopicRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return service.updateTopic(id, request, jwt);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.deleteTopic(id, jwt);
        return ResponseEntity.noContent().build();
    }
}