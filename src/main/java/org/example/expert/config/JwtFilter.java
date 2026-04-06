package org.example.expert.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.expert.domain.common.dto.AuthUser;
import org.example.expert.domain.user.enums.UserRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final MeterRegistry meterRegistry;
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String bearerToken = request.getHeader("Authorization");

        String token = jwtUtil.substringToken(bearerToken);

        try {
            if (StringUtils.hasText(token)) {

                Claims claims = jwtUtil.extractClaims(token);
                if (claims == null) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "사용 할 수 없는 JWT 인듯");
                    return;
                }

                Long memberId = Long.parseLong(claims.getSubject());
                String email = claims.get("email", String.class);
                String role = claims.get("userRole", String.class);
                String nickname = claims.get("nickname", String.class);
                AuthUser authUser = new AuthUser(memberId, email,  UserRole.valueOf(role), nickname);

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        authUser,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(StringUtils.hasText(role) ? role : "ROLE_USER"))
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                meterRegistry.counter("security_jwt_success").increment();
                log.debug("Security Context에 '{}' 인증 정보를 저장했습니다.", memberId);
            }
        } catch (ExpiredJwtException e) {
            meterRegistry.counter("security_jwt_failure", "type", "expired").increment();
            log.warn("만료된 JWT 토큰입니다: {}", e.getMessage());
            request.setAttribute("exception", e);
        } catch (SecurityException | MalformedJwtException e) {
            meterRegistry.counter("security_jwt_failure", "type", "invalid").increment();
            log.warn("잘못된 JWT 서명입니다: {}", e.getMessage());
            request.setAttribute("exception", e);
        } catch (UnsupportedJwtException e) {
            meterRegistry.counter("security_jwt_failure", "type", "unsupported").increment();
            log.warn("지원되지 않는 JWT 토큰입니다: {}", e.getMessage());
            request.setAttribute("exception", e);
        } catch (Exception e) {
            meterRegistry.counter("security_jwt_failure", "type", "unknown").increment();
            log.error("JWT 검증 중 알 수 없는 예외가 발생했습니다: {}", e.getMessage());
            request.setAttribute("exception", e);
        }

        filterChain.doFilter(request, response);
    }


}