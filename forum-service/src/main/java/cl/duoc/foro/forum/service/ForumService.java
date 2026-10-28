package cl.duoc.foro.forum.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import cl.duoc.foro.common.JwtTokens;
import cl.duoc.foro.forum.dto.ForumDtos.*;
import cl.duoc.foro.forum.entity.Category;
import cl.duoc.foro.forum.entity.Comment;
import cl.duoc.foro.forum.entity.Topic;
import cl.duoc.foro.forum.repository.CategoryRepository;
import cl.duoc.foro.forum.repository.CommentRepository;
import cl.duoc.foro.forum.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class ForumService {
    private final CategoryRepository categories;
    private final TopicRepository topics;
    private final CommentRepository comments;

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categories.findAll(Sort.by("id")).stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse category(Long id) {
        return CategoryResponse.from(findCategory(id));
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        if (categories.existsByNameIgnoreCase(request.name().strip())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoria ya existe");
        }
        Category category = new Category();
        category.setName(request.name().strip());
        category.setDescription(request.description().strip());
        return CategoryResponse.from(categories.saveAndFlush(category));
    }

    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        if (categories.existsByNameIgnoreCaseAndIdNot(request.name().strip(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoria ya existe");
        }
        category.setName(request.name().strip());
        category.setDescription(request.description().strip());
        return CategoryResponse.from(categories.saveAndFlush(category));
    }

    public void deleteCategory(Long id) {
        Category category = findCategory(id);
        if (topics.existsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoria tiene temas asociados");
        }
        categories.delete(category);
    }

    @Transactional(readOnly = true)
    public Page<TopicResponse> search(String query, Long categoryId, Pageable pageable) {
        String pattern = "%" + query.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<Topic> criteria = (root, selection, builder) ->
                builder.like(builder.lower(root.get("title")), pattern, '\\');
        if (categoryId != null) {
            criteria = criteria.and((root, selection, builder) ->
                    builder.equal(root.get("category").get("id"), categoryId));
        }
        return topics.findAll(criteria, pageable).map(TopicResponse::from);
    }

    @Transactional(readOnly = true)
    public TopicResponse topic(Long id) {
        return TopicResponse.from(findTopic(id));
    }

    public TopicResponse createTopic(TopicRequest request, Jwt jwt) {
        Topic topic = new Topic();
        topic.setCategory(findCategory(request.categoryId()));
        topic.setTitle(request.title().strip());
        topic.setContent(request.content().strip());
        topic.setAuthorId(JwtTokens.userId(jwt));
        topic.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return TopicResponse.from(topics.save(topic));
    }

    public TopicResponse updateTopic(Long id, TopicRequest request, Jwt jwt) {
        Topic topic = findTopic(id);
        checkOwner(topic.getAuthorId(), jwt);
        topic.setCategory(findCategory(request.categoryId()));
        topic.setTitle(request.title().strip());
        topic.setContent(request.content().strip());
        return TopicResponse.from(topics.save(topic));
    }

    public void deleteTopic(Long id, Jwt jwt) {
        Topic topic = findTopic(id);
        checkOwner(topic.getAuthorId(), jwt);
        comments.deleteForTopic(id);
        topics.delete(topic);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> comments(Long topicId, Pageable pageable) {
        findTopic(topicId);
        return comments.findByTopicId(topicId, pageable).map(CommentResponse::from);
    }

    @Transactional(readOnly = true)
    public CommentResponse comment(Long topicId, Long id) {
        return CommentResponse.from(findComment(topicId, id));
    }

    public CommentResponse createComment(Long topicId, CommentRequest request, Jwt jwt) {
        Comment comment = new Comment();
        comment.setTopic(findTopic(topicId));
        comment.setContent(request.content().strip());
        comment.setAuthorId(JwtTokens.userId(jwt));
        comment.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return CommentResponse.from(comments.save(comment));
    }

    public CommentResponse updateComment(Long topicId, Long id, CommentRequest request, Jwt jwt) {
        Comment comment = findComment(topicId, id);
        checkOwner(comment.getAuthorId(), jwt);
        comment.setContent(request.content().strip());
        return CommentResponse.from(comments.save(comment));
    }

    public void deleteComment(Long topicId, Long id, Jwt jwt) {
        Comment comment = findComment(topicId, id);
        checkOwner(comment.getAuthorId(), jwt);
        comments.delete(comment);
    }

    private Category findCategory(Long id) {
        return categories.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria no encontrada"));
    }

    private Topic findTopic(Long id) {
        return topics.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Tema no encontrado"));
    }

    private Comment findComment(Long topicId, Long id) {
        return comments.findByIdAndTopicId(id, topicId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentario no encontrado en este tema"));
    }

    private void checkOwner(Long authorId, Jwt jwt) {
        if (!JwtTokens.isAdmin(jwt) && !authorId.equals(JwtTokens.userId(jwt))) {
            throw new AccessDeniedException("Solo el autor o un administrador");
        }
    }
}