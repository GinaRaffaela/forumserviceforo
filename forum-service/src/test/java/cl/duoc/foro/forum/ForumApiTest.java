package cl.duoc.foro.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import cl.duoc.foro.common.JwtTokens;
import cl.duoc.foro.forum.repository.CategoryRepository;
import cl.duoc.foro.forum.repository.CommentRepository;
import cl.duoc.foro.forum.repository.TopicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForumApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtEncoder encoder;
    @Autowired CategoryRepository categories;
    @Autowired TopicRepository topics;
    @Autowired CommentRepository comments;
    String admin;
    String owner;
    String other;
    long categoryId;

    @BeforeEach
    void setup() throws Exception {
        comments.deleteAll();
        topics.deleteAll();
        categories.deleteAll();
        admin = "Bearer " + JwtTokens.issue(encoder, 1L, "ADMIN");
        owner = "Bearer " + JwtTokens.issue(encoder, 2L, "USER");
        other = "Bearer " + JwtTokens.issue(encoder, 3L, "USER");
        categoryId = createCategory("Tecnologia");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void demoCreatesFiveCategoriesAndThreeTopicsAndCommentsUsingRealUserIds() {
        categories.deleteAll();
        var builder = org.springframework.web.client.RestClient.builder().baseUrl("http://users");
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        for (long id : new long[]{51, 52, 53}) {
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://users/api/auth/login"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                            "{\"user\":{\"id\":" + id + "}}", MediaType.APPLICATION_JSON));
        }
        var seed = new cl.duoc.foro.forum.config.ForumDemoData(categories, topics, comments,
                builder.build(), "Test12345!");
        seed.run();
        seed.run();
        assertThat(categories.count()).isEqualTo(5);
        assertThat(topics.count()).isEqualTo(3);
        assertThat(comments.count()).isEqualTo(3);
        assertThat(topics.findAll()).extracting(cl.duoc.foro.forum.entity.Topic::getAuthorId)
                .containsExactlyInAnyOrder(51L, 52L, 53L);
        server.verify();
    }

    @Test
    void categoryCrudIsPublicToReadAndAdminOnlyToWrite() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Tecnologia"));
        mvc.perform(get("/api/categories/{id}", categoryId)).andExpect(status().isOk());
        mvc.perform(post("/api/categories").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Otros\",\"description\":\"Pruebas\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/categories").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"tecnologia\",\"description\":\"Duplicada\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/categories/{id}", categoryId).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ciencia\",\"description\":\"Actualizada\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ciencia"));
        mvc.perform(delete("/api/categories/{id}", categoryId).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/categories/{id}", categoryId)).andExpect(status().isNotFound());
    }

    @Test
    void topicAndCommentCrudAndSearch() throws Exception {
        long topicId = createTopic("Aprender Spring");
        mvc.perform(get("/api/topics").param("q", "SPRING").param("categoryId", Long.toString(categoryId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/topics").param("q", "inexistente"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/topics/{id}", topicId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorId").value(2));
        mvc.perform(put("/api/topics/{id}", topicId).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(topicPayload("Spring actualizado")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Spring actualizado"));
        long commentId = createComment(topicId, other);
        mvc.perform(get("/api/topics/{id}/comments", topicId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/topics/{topic}/comments/{id}", topicId, commentId)).andExpect(status().isOk());
        mvc.perform(put("/api/topics/{topic}/comments/{id}", topicId, commentId)
                .header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Comentario editado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Comentario editado"));
        mvc.perform(delete("/api/topics/{topic}/comments/{id}", topicId, commentId).header("Authorization", other))
                .andExpect(status().isNoContent());
        createComment(topicId, other);
        mvc.perform(delete("/api/topics/{id}", topicId).header("Authorization", owner)).andExpect(status().isNoContent());
        assertThat(comments.count()).isZero();
        assertThat(topics.count()).isZero();
        mvc.perform(get("/api/topics/{id}", topicId)).andExpect(status().isNotFound());
    }

    @Test
    void enforcesOwnershipAndParentChildRelations() throws Exception {
        long topicId = createTopic("Seguridad");
        long commentId = createComment(topicId, other);
        mvc.perform(put("/api/topics/{id}", topicId).header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content(topicPayload("No permitido")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/topics/{id}", topicId).header("Authorization", other))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/topics/{topic}/comments/{id}", topicId, commentId).header("Authorization", owner))
                .andExpect(status().isForbidden());
        long anotherTopic = createTopic("Otro tema");
        mvc.perform(get("/api/topics/{topic}/comments/{id}", anotherTopic, commentId)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/categories/{id}", categoryId).header("Authorization", admin))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/topics/{topic}/comments/{id}", topicId, commentId).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/topics/{id}", topicId).header("Authorization", admin)).andExpect(status().isNoContent());
    }

    @Test
    void rejectsUnauthenticatedWritesMalformedDataAndInvalidPages() throws Exception {
        mvc.perform(post("/api/topics").contentType(MediaType.APPLICATION_JSON).content(topicPayload("Sin token")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/topics").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"content\":\"\",\"categoryId\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/topics").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Valido\",\"content\":\"Contenido\",\"categoryId\":99999}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/topics").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/topics").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/topics/not-a-number")).andExpect(status().isBadRequest());
        createTopic("Literal 100% real");
        mvc.perform(get("/api/topics").param("q", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    private long createCategory(String name) throws Exception {
        String response = mvc.perform(post("/api/categories").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"Categoria de prueba\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("id").asLong();
    }

    private long createTopic(String title) throws Exception {
        String response = mvc.perform(post("/api/topics").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(topicPayload(title)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("id").asLong();
    }

    private long createComment(long topicId, String token) throws Exception {
        String response = mvc.perform(post("/api/topics/{id}/comments", topicId).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Mi comentario\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode comment = mapper.readTree(response);
        return comment.get("id").asLong();
    }

    private String topicPayload(String title) {
        return "{\"title\":\"" + title + "\",\"content\":\"Contenido del tema\",\"categoryId\":" + categoryId + "}";
    }
}