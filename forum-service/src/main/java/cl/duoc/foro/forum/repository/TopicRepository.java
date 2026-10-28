package cl.duoc.foro.forum.repository;

import cl.duoc.foro.forum.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TopicRepository extends JpaRepository<Topic, Long>, JpaSpecificationExecutor<Topic> {
    boolean existsByCategoryId(Long categoryId);
}