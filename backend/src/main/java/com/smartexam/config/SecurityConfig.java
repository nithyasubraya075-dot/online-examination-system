package com.smartexam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartexam.repository.Users;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService userDetailsService(Users users) {
        return email -> users.findByEmailIgnoreCase(email)
                .map(u -> User.withUsername(u.email).password(u.passwordHash).roles(u.role.name()).disabled(!u.enabled).build())
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/api/public/**", "/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/teacher/**").hasRole("TEACHER")
                .requestMatchers("/api/student/**").hasRole("STUDENT")
                .anyRequest().authenticated())
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login").usernameParameter("email")
                .successHandler((request, response, authentication) -> {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(response.getOutputStream(), Map.of("message", "Signed in", "role",
                            authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")));
                })
                .failureHandler((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(response.getOutputStream(), Map.of("message", "Email or password is incorrect."));
                }))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").logoutSuccessHandler((request, response, authentication) -> {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                mapper.writeValue(response.getOutputStream(), Map.of("message", "Signed out"));
            }))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(response.getOutputStream(), Map.of("message", "Please sign in to continue."));
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(response.getOutputStream(), Map.of("message", "You do not have permission to perform this action."));
                }));
        return http.build();
    }
}
