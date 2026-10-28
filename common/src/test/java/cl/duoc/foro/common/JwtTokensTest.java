package cl.duoc.foro.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtTokensTest {
    private final JwtTokens configuration = new JwtTokens();

    @Test
    void signsAndVerifiesIdentityAndRole() {
        var key = configuration.jwtKey("test-secret-with-at-least-32-bytes-long");
        String token = JwtTokens.issue(configuration.jwtEncoder(key), 3L, "USER");
        Jwt jwt = configuration.jwtDecoder(key).decode(token);
        assertThat(JwtTokens.userId(jwt)).isEqualTo(3L);
        assertThat(JwtTokens.isAdmin(jwt)).isFalse();
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void rejectsShortSecretsAndForgedTokens() {
        assertThatThrownBy(() -> configuration.jwtKey("short"))
                .isInstanceOf(IllegalArgumentException.class);
        var original = configuration.jwtKey("original-secret-with-32-bytes-minimum");
        var attacker = configuration.jwtKey("different-secret-with-32-bytes-minimum");
        String token = JwtTokens.issue(configuration.jwtEncoder(attacker), 1L, "ADMIN");
        assertThatThrownBy(() -> configuration.jwtDecoder(original).decode(token))
                .isInstanceOf(JwtException.class);
    }
}