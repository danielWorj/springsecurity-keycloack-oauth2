# Spring Security + Keycloak (OAuth2 Resource Server)

Projet d'exemple montrant comment sécuriser une API REST Spring Boot avec **Keycloak** en utilisant **OAuth2 / JWT**.

L'application joue le rôle de **Resource Server** : elle ne gère pas l'authentification elle-même. Elle valide les tokens JWT émis par Keycloak et applique des règles d'accès selon les **rôles** contenus dans le token.

---

## Sommaire

1. [Fonctionnement](#fonctionnement)
2. [Technologies](#technologies)
3. [Prérequis](#prérequis)
4. [Structure du projet](#structure-du-projet)
5. [Configuration de Keycloak](#configuration-de-keycloak)
6. [Configuration de l'application](#configuration-de-lapplication)
7. [Lancer l'application](#lancer-lapplication)
8. [Endpoints et règles d'accès](#endpoints-et-règles-daccès)
9. [Tester l'API](#tester-lapi)
10. [Dépannage](#dépannage)

---

## Fonctionnement

```
┌────────┐  1. login (user/password)   ┌──────────┐
│ Client │ ──────────────────────────► │ Keycloak │
│        │ ◄────────────────────────── │  :9090   │
└───┬────┘  2. access_token (JWT)      └────▲─────┘
    │                                       │ 4. clés publiques (JWKS)
    │ 3. GET /api/... + Authorization:      │
    │    Bearer <access_token>              │
    ▼                                  ┌────┴─────┐
┌──────────────────────────────────────┤ API Spring│
│  Valide la signature du JWT,         │  :8085   │
│  extrait les rôles, autorise/refuse  └──────────┘
└──────────────────────────────────────
```

1. Le client s'authentifie auprès de Keycloak et reçoit un **access token** (JWT).
2. Le client appelle l'API en envoyant le token dans l'en-tête `Authorization: Bearer <token>`.
3. L'API vérifie la signature du token grâce aux clés publiques de Keycloak (`jwk-set-uri`) et contrôle l'émetteur (`issuer-uri`).
4. La classe `JwtConverter` extrait les rôles du client `my-app-client` (claim `resource_access`) et les transforme en autorités Spring (`ROLE_user`, `ROLE_admin`).
5. Spring Security autorise ou refuse l'accès selon la route demandée.

---

## Technologies

| Outil | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Spring Security (OAuth2 Resource Server) | fournie par Spring Boot |
| Spring Web MVC | fournie par Spring Boot |
| Lombok | fournie par Spring Boot |
| Maven | via le wrapper `mvnw` |
| Keycloak | 24+ recommandé |

---

## Prérequis

- **JDK 21** installé (`java -version`)
- **Keycloak** démarré sur le port **9090** (voir ci-dessous), via Docker ou en installation locale
- Un IDE avec le plugin **Lombok** activé et le traitement des annotations (*Annotation Processing*) activé (IntelliJ : `Settings > Build > Compiler > Annotation Processors`)

---

## Structure du projet

```
springsecurity-keycloack-oauth2/
└── springsecuritykeycloack/
    ├── pom.xml
    └── src/main/
        ├── java/com/example/springsecuritykeycloack/
        │   ├── SpringsecuritykeycloackApplication.java   # Point d'entrée
        │   ├── controller/
        │   │   └── HomeController.java                   # Endpoints /api/home, /api/user, /api/admin
        │   └── security/
        │       ├── SpringSecurityConfig.java             # Règles d'accès + config JWT
        │       ├── JwtConverter.java                     # Extraction des rôles du token
        │       └── JwtConverterProperties.java           # Propriétés jwt.auth.converter.*
        └── resources/
            └── application.yaml                          # Configuration
```

| Classe | Rôle |
|---|---|
| `SpringSecurityConfig` | Définit la `SecurityFilterChain` : routes publiques / protégées, mode **stateless** (aucune session), activation du Resource Server JWT avec le convertisseur personnalisé. |
| `JwtConverter` | Convertit un `Jwt` en `JwtAuthenticationToken`. Fusionne les autorités standard (`SCOPE_...`) avec les rôles du client Keycloak préfixés par `ROLE_`. |
| `JwtConverterProperties` | Lit `jwt.auth.converter.resource-id` et `jwt.auth.converter.principal-attribute` depuis le YAML. |
| `HomeController` | Trois endpoints de démonstration (publique, utilisateur, administrateur). |

---

## Configuration de Keycloak

### 1. Démarrer Keycloak sur le port 9090

Avec Docker :

```bash
docker run -p 9090:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```

La console d'administration est alors disponible sur <http://localhost:9090>.

> Sur une version de Keycloak antérieure à la 26, utilise `KEYCLOAK_ADMIN` et `KEYCLOAK_ADMIN_PASSWORD` à la place des variables `KC_BOOTSTRAP_*`.

### 2. Créer le realm

1. Connecte-toi à la console d'administration.
2. Menu déroulant en haut à gauche → **Create realm**.
3. Nom : `SpringSecurityKeycloackRealm` (le nom doit être identique, majuscules comprises, à celui de `issuer-uri`).

### 3. Créer le client

1. **Clients → Create client**.
2. **Client ID** : `my-app-client`.
3. À l'étape *Capability config* : active **Direct access grants** (nécessaire pour récupérer un token avec un simple `curl` en mode login/mot de passe).
4. Enregistre.

### 4. Créer les rôles du client

Les rôles doivent être des **rôles de client** (et non des rôles de realm), car `JwtConverter` lit `resource_access.my-app-client.roles`.

1. **Clients → my-app-client → Roles → Create role**.
2. Crée deux rôles : `user` et `admin` (en minuscules, ils correspondent aux constantes `USER` et `ADMIN` de `SpringSecurityConfig`).

### 5. Créer les utilisateurs

1. **Users → Add user** (par exemple `alice` et `bob`), puis enregistre.
2. Onglet **Credentials** → **Set password** (décoche *Temporary*).
3. Onglet **Role mapping → Assign role** → filtre sur **Filter by clients** → choisis le rôle voulu :
   - `alice` : rôle `user`
   - `bob` : rôle `admin`

### 6. Vérifier la configuration

Le endpoint de découverte doit répondre :

```
http://localhost:9090/realms/SpringSecurityKeycloackRealm/.well-known/openid-configuration
```

---

## Configuration de l'application

Fichier `src/main/resources/application.yaml` :

```yaml
spring:
  application:
    name: springsecuritykeycloack

  # security config
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9090/realms/SpringSecurityKeycloackRealm # url qui delivre le token (champ <<issuer>> dans Keycloak)
          jwk-set-uri: ${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/certs # clés publiques (champ <<jwks_uri>> dans Keycloak)

# JWT Config
jwt:
  auth:
    converter:
      resource-id: my-app-client # nom du client defini sur Keycloak
      principal-attribute: preferred_username # claim utilisé comme nom d'utilisateur

server:
  port: 8085
```

| Propriété | Description |
|---|---|
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | URL du realm Keycloak. Le champ `iss` du token doit correspondre exactement. |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | URL où Spring récupère les clés publiques pour vérifier la signature. |
| `jwt.auth.converter.resource-id` | Identifiant du client Keycloak dont on lit les rôles. |
| `jwt.auth.converter.principal-attribute` | Claim utilisé comme nom du principal. Si absent, `sub` est utilisé. |
| `server.port` | Port de l'API. |

> ⚠️ **Attention à l'indentation YAML.** `jwt:` et `server:` sont à la **racine** du fichier, et non sous `spring:`. Une mauvaise indentation fait échouer le démarrage avec l'erreur `required a bean of type 'org.springframework.security.oauth2.jwt.JwtDecoder'` (voir [Dépannage](#dépannage)).

---

## Lancer l'application

Depuis le dossier `springsecuritykeycloack/` :

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

Ou directement depuis l'IDE en exécutant `SpringsecuritykeycloackApplication`.

L'API est disponible sur <http://localhost:8085>.

---

## Endpoints et règles d'accès

| Méthode | URL | Accès | Réponse |
|---|---|---|---|
| GET | `/api/home` | Public | `Welcome to the Home Page!` |
| GET | `/api/user` | Rôle `user` | `Welcome to the User Page!` |
| GET | `/api/admin` | Rôle `admin` | `Welcome to the Admin Page!` |
| * | toute autre route | Authentifié | — |

Règles définies dans `SpringSecurityConfig` :

```java
.requestMatchers("/api/home").permitAll()
.requestMatchers("/api/user/**").hasRole("user")
.requestMatchers("/api/admin/**").hasRole("admin")
.anyRequest().authenticated()
```

> `hasRole("user")` cherche l'autorité `ROLE_user`. C'est pour cela que `JwtConverter` ajoute le préfixe `ROLE_` aux rôles lus dans le token.

Codes de retour :

- `401 Unauthorized` : token absent, expiré ou invalide
- `403 Forbidden` : token valide mais rôle insuffisant

---

## Tester l'API

### 1. Récupérer un token

```bash
curl -X POST "http://localhost:9090/realms/SpringSecurityKeycloackRealm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=my-app-client" \
  -d "username=alice" \
  -d "password=<mot_de_passe>"
```

La réponse contient un champ `access_token`.

### 2. Appeler l'API

```bash
# Endpoint public (pas de token nécessaire)
curl http://localhost:8085/api/home

# Endpoint protégé
curl http://localhost:8085/api/user \
  -H "Authorization: Bearer <access_token>"
```

### Résultats attendus

| Utilisateur | `/api/home` | `/api/user` | `/api/admin` |
|---|---|---|---|
| Sans token | 200 | 401 | 401 |
| `alice` (rôle `user`) | 200 | 200 | 403 |
| `bob` (rôle `admin`) | 200 | 403 | 200 |

> Pour inspecter le contenu d'un token, colle-le sur <https://jwt.io> (ne le fais jamais avec un token de production) et vérifie la présence de `resource_access.my-app-client.roles`.

---

## Dépannage

### `Parameter 0 of method setFilterChains ... required a bean of type 'JwtDecoder'`

Spring ne trouve pas les propriétés `spring.security.oauth2.resourceserver.jwt.*`, donc le bean `JwtDecoder` n'est pas créé. Vérifie dans `application.yaml` :

- l'**indentation** : `security` doit être directement sous `spring`, et `jwt` / `server` à la racine ;
- l'**espace après les deux-points** : `issuer-uri: http://...` (et non `issuer-uri:http://...`).

### Le démarrage échoue ou les requêtes renvoient `401` alors que le token est valide

- Keycloak n'est pas démarré, ou pas sur le port 9090.
- Le nom du realm dans `issuer-uri` est différent de celui créé dans Keycloak (la casse compte).
- Le champ `iss` du token ne correspond pas à `issuer-uri`. Cela arrive quand le token est obtenu via une autre URL, par exemple `127.0.0.1` au lieu de `localhost`.

### `403 Forbidden` alors que l'utilisateur a bien un rôle

- Le rôle a été créé comme **rôle de realm** au lieu d'un **rôle de client** de `my-app-client`.
- Le rôle n'est pas assigné à l'utilisateur (onglet *Role mapping*).
- La casse ne correspond pas : le rôle Keycloak doit être `user` / `admin`, comme dans `SpringSecurityConfig`.
- `resource-id` ne correspond pas au Client ID du client Keycloak.

### Le nom d'utilisateur est `null`

Le claim indiqué dans `principal-attribute` n'existe pas dans le token. Utilise `preferred_username` (fourni par défaut par Keycloak) ou supprime la propriété pour utiliser `sub`.

### Erreurs de compilation liées à Lombok (`getResourceId()` introuvable...)

Active le plugin Lombok et l'*Annotation Processing* dans ton IDE.

---

## Pistes d'amélioration

- Ajouter la prise en charge des **rôles de realm** (`realm_access.roles`) dans `JwtConverter`
- Ajouter des tests avec `spring-security-test` (`SecurityMockMvcRequestPostProcessors.jwt()`)
- Configurer le **CORS** pour une application front-end
- Passer à un client **confidentiel** avec un *client secret* et le flux *Authorization Code + PKCE* pour un front-end
