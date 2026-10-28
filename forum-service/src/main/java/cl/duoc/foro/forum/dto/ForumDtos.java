package cl.duoc.foro.forum.dto;

import java.time.LocalDateTime;
import cl.duoc.foro.forum.entity.Category;
import cl.duoc.foro.forum.entity.Comment;
import cl.duoc.foro.forum.entity.Topic;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class ForumDtos {
    private ForumDtos() {}

    public record CategoryRequest(@NotBlank @Size(max = 80) String name,
                                  @NotBlank @Size(max = 500) String description) {}
    public record TopicRequest(@NotBlank @Size(max = 180) String title,
                               @NotBlank @Size(max = 20000) String content,
                               @NotNull @Positive Long categoryId) {}
    public record CommentRequest(@NotBlank @Size(max = 5000) String content) {}

    public record CategoryResponse(Long id, String name, String description) {
        public static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getDescription());
        }
    }

    public record TopicResponse(Long id, String title, String content, Long authorId,
                                Long categoryId, LocalDateTime createdAt) {
        public static TopicResponse from(Topic topic) {
            return new TopicResponse(topic.getId(), topic.getTitle(), topic.getContent(), topic.getAuthorId(),
                    topic.getCategory().getId(), topic.getCreatedAt());
        }
    }

    public record CommentResponse(Long id, String content, Long authorId, Long topicId, LocalDateTime createdAt) {
        public static CommentResponse from(Comment comment) {
            return new CommentResponse(comment.getId(), comment.getContent(), comment.getAuthorId(),
                    comment.getTopic().getId(), comment.getCreatedAt());
        }
    }
}