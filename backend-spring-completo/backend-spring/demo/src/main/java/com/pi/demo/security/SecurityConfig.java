package com.pi.demo.security;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.pi.demo.config.SegurancaProperties;
import com.pi.demo.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletResponse;

/**
 * API stateless com token Bearer. Perfis: OPERADOR (opera a mesa e consulta) e ADMIN (tudo, inclusive
 * usuarios e esteiras). Com seguranca.habilitada=false a API fica totalmente aberta.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Evita que o Spring Boot crie o usuario padrao "user" com senha aleatoria no log. */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origens-permitidas:*}") String origens) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of(origens.split("\\s*,\\s*")));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", cors);
        return fonte;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SegurancaProperties props,
                                           TokenService tokens,
                                           UsuarioRepository usuarios) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!props.habilitada()) {
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }

        http.addFilterBefore(new TokenAuthFilter(tokens, usuarios), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                escrever(res, 401, "Unauthorized", "Autenticacao necessaria: envie 'Authorization: Bearer <token>'.", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) ->
                                escrever(res, 403, "Forbidden", "Seu perfil nao tem permissao para esta operacao.", req.getRequestURI())))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/usuarios/**", "/api/simulacao/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/esteiras/**", "/api/producao/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/esteiras/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/esteiras/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("OPERADOR", "ADMIN")
                        .anyRequest().denyAll());
        return http.build();
    }

    private static void escrever(HttpServletResponse res, int status, String titulo, String detalhe, String instancia)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        String json = "{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":" + status
                + ",\"detail\":\"" + detalhe.replace("\"", "'") + "\",\"instance\":\"" + instancia.replace("\"", "") + "\"}";
        res.getWriter().write(json);
    }
}
