package com.easyhome.config;

import com.easyhome.security.ApiKeyAuthFilter;
import com.easyhome.security.JwtAuthFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置
 *
 * 双通道认证策略（路径完全隔离）:
 *   - 管理端 /api/**：仅 JWT 认证（账号密码登录），JwtAuthFilter 只处理此路径
 *   - MCP 端 /mcp/**：仅 API Key 认证（Bearer Token），ApiKeyAuthFilter 只处理此路径
 *
 * Filter 路径隔离机制（shouldNotFilter）：
 *   - JwtAuthFilter.shouldNotFilter → 只处理 /api/**
 *   - ApiKeyAuthFilter.shouldNotFilter → 只处理 /mcp/**
 *   两个 Filter 互不干扰，不会出现 Token 被错误解析的情况
 *
 * 无状态（STATELESS），不使用 Session，禁用 CSRF。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ApiKeyAuthFilter apiKeyAuthFilter;

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 覆盖 Spring Security 默认的 InMemoryUserDetailsManager，
     * 消除 "Using generated security password" 启动提示。
     * 实际认证由 JwtAuthFilter / ApiKeyAuthFilter 处理，此 Bean 不会被调用。
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 禁用 CSRF（REST API 无需 CSRF 保护）
                .csrf(AbstractHttpConfigurer::disable)
                // 禁用默认的表单登录和 Basic 认证
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 无状态，不创建 Session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 路径权限规则
                .authorizeHttpRequests(auth -> auth
                        // 异步分发（SSE 流式完成后 Tomcat 的二次 dispatch）放行，避免认证上下文丢失导致 403
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        // 健康检查公开
                        .requestMatchers("/actuator/health").permitAll()
                        // Spring Boot 默认错误页公开（否则内部 forward 到 /error 会被拦截返回 401）
                        .requestMatchers("/error").permitAll()
                        // 管理端登录接口公开
                        .requestMatchers("/api/auth/login").permitAll()
                        // 忘记密码：发送验证码 / 重置密码 公开
                        .requestMatchers("/api/auth/send-reset-code", "/api/auth/reset-password").permitAll()
                        // 管理端接口需要 JWT 认证，方法级别通过 @PreAuthorize 做角色校验
                        .requestMatchers("/api/**").authenticated()
                        // MCP 端点需要 API Key 认证
                        .requestMatchers("/mcp/**").authenticated()
                        // 其余所有请求必须认证
                        .anyRequest().authenticated()
                )
                // 未认证时返回 401（而非重定向到登录页）
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Filter 注册：通过 shouldNotFilter 实现路径隔离，无需关心顺序
                // JwtAuthFilter 只处理 /api/**，ApiKeyAuthFilter 只处理 /mcp/**
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
