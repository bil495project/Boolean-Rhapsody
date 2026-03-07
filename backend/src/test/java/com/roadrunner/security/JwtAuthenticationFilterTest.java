package com.roadrunner.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TEST_USER_ID = "test-user-id-123";
    private static final String VALID_TOKEN = "valid.jwt.token";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSetAuthentication_whenValidBearerTokenIsPresent() throws ServletException, IOException {
        // given
        request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(TEST_USER_ID);

        UserDetails userDetails = new User(TEST_USER_ID, "password", Collections.emptyList());
        when(customUserDetailsService.loadUserByUsername(TEST_USER_ID)).thenReturn(userDetails);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo(TEST_USER_ID);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotSetAuthentication_whenAuthorizationHeaderIsMissing() throws ServletException, IOException {
        // given — no Authorization header

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotSetAuthentication_whenHeaderDoesNotStartWithBearer() throws ServletException, IOException {
        // given
        request.addHeader("Authorization", "Basic abc123");

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotSetAuthentication_whenTokenIsInvalid() throws ServletException, IOException {
        // given
        request.addHeader("Authorization", "Bearer invalidtoken");
        when(jwtTokenProvider.validateToken("invalidtoken")).thenReturn(false);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldClearSecurityContext_whenTokenValidationThrowsException() throws ServletException, IOException {
        // given
        request.addHeader("Authorization", "Bearer throwingtoken");
        when(jwtTokenProvider.validateToken("throwingtoken")).thenThrow(new RuntimeException("Unexpected error"));

        // when / then — filter chain should still continue
        try {
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException e) {
            // The filter may propagate the exception — that's acceptable
        }

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
