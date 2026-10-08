package com.example.springsecuritykeycloack.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SpringSecurityConfig {
    private static final String ADMIN = "admin";
    private static final String USER = "user";
    private final JwtConverter jwtConverter;
    @Bean
    public SecurityFilterChain configure(HttpSecurity http) throws Exception{
        // cette methode permet de configurer la securite de l'application, on va configurer les autorisations pour les differentes routes, on va aussi configurer le serveur de ressources pour qu'il utilise JWT et qu'il utilise notre JwtConverter pour extraire les informations du token
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/home").permitAll()
                        .requestMatchers("/api/user/**").hasRole(USER)
                        .requestMatchers("/api/admin/**").hasRole(ADMIN)
                        .anyRequest().authenticated()
                ) // on configure les autorisations pour les differentes routes, toutes les routes qui ne sont pas configurees ici seront protegees par l'authentification
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                )) // on ne veut pas de session, on veut que chaque requete soit authentifiee
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtConverter)
                        )
                )// on configure le serveur de ressources pour qu'il utilise JWT et qu'il utilise notre JwtConverter pour extraire les informations du token
                .build();
    }

}
