package com.example.springsecuritykeycloack.security;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component // pour qu;il soit charge
@RequiredArgsConstructor
public class JwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    // Classe qui permet d'extraire les informations contenys dans le token

    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    private final JwtConverterProperties jwtConverterProperties;
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Le but de cette methode est de convertir le token en un objet JwtAuthenticationToken qui contient les informations du token et les roles de l'utilisateur
        //Pour cela, on va d'abord recuperer les roles du token grace a la methode extractResourceRoles, puis on va les combiner avec les roles du token grace a la methode convert de JwtGrantedAuthoritiesConverter

        Collection<GrantedAuthority> authorities = Stream.concat(
            jwtGrantedAuthoritiesConverter.convert(jwt).stream(),
                extractResourceRoles(jwt).stream()
        ).collect(Collectors.toSet());

        return new JwtAuthenticationToken(jwt,authorities,getPrincipalClaimName(jwt));
    }

    private Collection<? extends  GrantedAuthority> extractResourceRoles(Jwt jwt) {
        //Le but de cette methode est d'extraire les roles du client my-app-client dans le token
        //Pour cela on recupere d'abord le claim resource_access dans le token, puis on recupere le client my-app-client et enfin on recupere les roles de ce client
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        Map<String, Object> resource ;
        Collection<String> resourceRoles;

        if (resourceAccess==null
                || (resource = (Map<String,Object>) resourceAccess.get(jwtConverterProperties.getResourceId())) ==null
                || (resourceRoles = (Collection<String>) resource.get("roles")) == null) {
            // si le ressource access est null ou alors
            // uq'on essaye de recyperer le client my-app-client et que c'est aussi nul
            // ou alors qu'on ne trouve aucun role la dedans
            // on retourne un set vide
            return Set.of();
        }
        // Si on a bien recupere les roles, on les transforme en SimpleGrantedAuthority et on les retourne
        return resourceRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_"+role))
                .collect(Collectors.toSet());
    }

    private String getPrincipalClaimName(Jwt jwt) {
        String clainName = JwtClaimNames.SUB;
        if (jwtConverterProperties.getPrincipalAttribute() != null) {
            //Par defaut on prend le SUB dans le cas ou cette propriete n'est pas renseignee dans le fichier de configuration
            clainName = jwtConverterProperties.getPrincipalAttribute();
        }
        return jwt.getClaim(clainName);
    }
}
