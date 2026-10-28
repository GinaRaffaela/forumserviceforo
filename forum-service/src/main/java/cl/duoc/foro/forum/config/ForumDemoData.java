package cl.duoc.foro.forum.config;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import cl.duoc.foro.forum.entity.Category;
import cl.duoc.foro.forum.entity.Comment;
import cl.duoc.foro.forum.entity.Topic;
import cl.duoc.foro.forum.repository.CategoryRepository;
import cl.duoc.foro.forum.repository.CommentRepository;
import cl.duoc.foro.forum.repository.TopicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Component
@Profile("demo")
public class ForumDemoData implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(ForumDemoData.class);
    private final CategoryRepository categories;
    private final TopicRepository topics;
    private final CommentRepository comments;
    private final RestClient demoUsersClient;
    private final String password;

    public ForumDemoData(CategoryRepository categories, TopicRepository topics, CommentRepository comments,
                         RestClient demoUsersClient, @Value("${DEMO_PASSWORD:Demo1234}") String password) {
        this.categories = categories;
        this.topics = topics;
        this.comments = comments;
        this.demoUsersClient = demoUsersClient;
        this.password = password;
    }

    @Configuration
    @Profile("demo")
    public static class DemoClientConfiguration {
        @Bean
        RestClient demoUsersClient(@Value("${DEMO_USERS_URL:http://localhost:8081}") String url) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(5));
            factory.setReadTimeout(Duration.ofSeconds(10));
            return RestClient.builder().baseUrl(url).requestFactory(factory).build();
        }
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (categories.count() != 0 || topics.count() != 0 || comments.count() != 0) {
            log.info("Datos demo omitidos: el esquema del foro ya contiene registros");
            return;
        }
        Long admin = loginId("admin@foro.test");
        Long ana = loginId("ana@foro.test");
        Long luis = loginId("luis@foro.test");
        List<Category> created = new ArrayList<>();
        for (String name : List.of("Tecnologia", "Videojuegos", "Ciencia", "Deportes", "Cine y series")) {
            Category category = new Category();
            category.setName(name);
            category.setDescription("Conversaciones sobre " + name.toLowerCase(java.util.Locale.ROOT));
            created.add(categories.save(category));
        }
        Topic spring = topic("Primeros pasos con Spring Boot", "Compartamos recursos para aprender Java.", ana, created.get(0));
        Topic games = topic("Juegos cooperativos recomendados", "Que juegos recomiendan para jugar en equipo?", luis, created.get(1));
        Topic science = topic("Exploracion espacial", "Conversemos sobre las nuevas misiones espaciales.", admin, created.get(2));
        comment("Recomiendo revisar la documentacion oficial de Spring.", luis, spring);
        comment("Me gustan los juegos de estrategia cooperativa.", ana, games);
        comment("Las misiones a Marte son muy interesantes.", ana, science);
        log.info("Creadas cinco categorias, tres temas y tres comentarios de demostracion");
    }

    private Long loginId(String email) {
        JsonNode response = demoUsersClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("email", email, "password", password))
                .retrieve().body(JsonNode.class);
        if (response == null || !response.path("user").path("id").canConvertToLong()
                || response.path("user").path("id").asLong() < 1) {
            throw new IllegalStateException("No se pudo resolver el usuario demo: " + email);
        }
        return response.path("user").path("id").asLong();
    }

    private Topic topic(String title, String content, Long author, Category category) {
        Topic topic = new Topic();
        topic.setTitle(title);
        topic.setContent(content);
        topic.setAuthorId(author);
        topic.setCategory(category);
        topic.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return topics.save(topic);
    }

    private void comment(String content, Long author, Topic topic) {
        Comment comment = new Comment();
        comment.setContent(content);
        comment.setAuthorId(author);
        comment.setTopic(topic);
        comment.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        comments.save(comment);
    }
}