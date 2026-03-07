package com.roadrunner.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private static final String TEST_SECRET = "dGVzdFNlY3JldEtleUZvclJvYWRSdW5uZXJUZXN0aW5nMTIz";
    private static final long TEST_EXPIRATION_MS = 3600000L;
    private static final String TEST_USER_ID = "test-user-id-123";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, TEST_EXPIRATION_MS);
    }

    // --- generateToken ---

    @Test
    void shouldReturnNonNullToken_whenUserIdIsValid() {
        // given
        String userId = TEST_USER_ID;

        // when
        String token = jwtTokenProvider.generateToken(userId);

        // then
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void shouldReturnTokenContainingUserId_whenTokenIsGenerated() {
        // given
        String userId = TEST_USER_ID;

        // when
        String token = jwtTokenProvider.generateToken(userId);
        String extractedUserId = jwtTokenProvider.getUserIdFromToken(token);

        // then
        assertThat(extractedUserId).isEqualTo(userId);
    }

    // --- getUserIdFromToken ---

    @Test
    void shouldReturnCorrectUserId_whenTokenIsValid() {
        // given
        String userId = "another-user-456";
        String token = jwtTokenProvider.generateToken(userId);

        // when
        String result = jwtTokenProvider.getUserIdFromToken(token);

        // then
        assertThat(result).isEqualTo(userId);
    }

    @Test
    void shouldThrowException_whenTokenIsMalformed() {
        // given
        String malformedToken = "not.a.token";

        // when / then
        assertThatThrownBy(() -> jwtTokenProvider.getUserIdFromToken(malformedToken))
                .isInstanceOf(JwtException.class);
    }

    // --- validateToken ---

    @Test
    void shouldReturnTrue_whenTokenIsValid() {
        // given
        String token = jwtTokenProvider.generateToken(TEST_USER_ID);

        // when
        boolean result = jwtTokenProvider.validateToken(token);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalse_whenTokenIsMalformed() {
        // given
        String malformedToken = "this.is.not.valid";

        // when
        boolean result = jwtTokenProvider.validateToken(malformedToken);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalse_whenTokenIsExpired() {
        // given
        JwtTokenProvider expiredProvider = new JwtTokenProvider(TEST_SECRET, -1000L);
        String token = expiredProvider.generateToken(TEST_USER_ID);

        // when
        boolean result = expiredProvider.validateToken(token);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalse_whenTokenIsNull() {
        // given
        String token = null;

        // when
        boolean result = jwtTokenProvider.validateToken(token);

        // then
        assertThat(result).isFalse();
    }
}
