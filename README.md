# Spring Security + Keycloak (OAuth2 Resource Server)

API REST Spring Boot sécurisée par **Keycloak** avec des tokens **JWT**. L'application valide les tokens et applique les règles d'accès selon les rôles du client Keycloak.

## Stack

Java 21 · Spring Boot 4.1.1 · Spring Security (OAuth2 Resource Server) · Lombok · Maven · Keycloak

## Prérequis

- JDK 21
- Keycloak sur le port **9090**
- Plugin Lombok activé dans l'IDE

## Configuration Keycloak

1. Créer le realm `SpringSecurityKeycloackRealm`
2. Créer le client `my-app-client` (activer *Direct access grants*)
3. Créer les **rôles de client** `user` et `admin`
4. Créer des utilisateurs et leur assigner un rôle (*Role mapping*)

## Configuration de l'application

`src/main/resources/application.yaml` :

```yaml
spring:
  application:
    name: springsecuritykeycloack
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9090/realms/SpringSecurityKeycloackRealm
          jwk-set-uri: ${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/certs

jwt:
  auth:
    converter:
      resource-id: my-app-client
      principal-attribute: preferred_username

server:
  port: 8085
```

> Attention à l'indentation : `jwt:` et `server:` sont à la racine, et il faut un espace après chaque `:`. Sinon, le démarrage échoue avec `required a bean of type 'JwtDecoder'`.

## Lancer

```bash
cd springsecuritykeycloack
./mvnw spring-boot:run      # Windows : mvnw.cmd spring-boot:run
```

L'API écoute sur <http://localhost:8085>.

## Endpoints

| URL | Accès |
|---|---|
| `GET /api/home` | Public |
| `GET /api/user` | Rôle `user` |
| `GET /api/admin` | Rôle `admin` |
| Autres routes | Authentifié |

## Tester

```bash
# 1. Récupérer un token
curl -X POST "http://localhost:9090/realms/SpringSecurityKeycloackRealm/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=my-app-client" \
  -d "username=<user>" -d "password=<mot_de_passe>"

# 2. Appeler l'API
curl http://localhost:8085/api/user -H "Authorization: Bearer <access_token>"
```

Codes de retour : `401` token absent ou invalide · `403` rôle insuffisant.

## Dépannage

- **Échec au démarrage (`JwtDecoder`)** : vérifier l'indentation du YAML.
- **401 avec un token valide** : Keycloak arrêté, nom du realm incorrect, ou `iss` du token différent de `issuer-uri` (`localhost` vs `127.0.0.1`).
- **403 avec un rôle assigné** : le rôle doit être un rôle de **client** `my-app-client`, avec la même casse (`user` / `admin`).