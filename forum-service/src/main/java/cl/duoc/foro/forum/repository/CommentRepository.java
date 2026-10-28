package cl.duoc.foro.forum.repository;

import java.util.Optional;
import cl.duoc.foro.forum.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByTopicId(Long topicId, Pageable pageable);
    Optional<Comment> findByIdAndTopicId(Long id, Long topicId);

    @Modifying
    @Query("delete from Comment comment where comment.topic.id = :topicId")
    void deleteForTopic(@Param("topicId") Long topicId);
}