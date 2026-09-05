package xyz.tcheeric.cashu.vault.db.client;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import xyz.tcheeric.cashu.vault.db.model.BaseEntity;

import java.util.Collections;
import java.util.List;

@Data
@Slf4j
/**
 * Generic REST client for interacting with the vault service.
 *
 * <p>The client provides CRUD-like operations for entities stored in the
 * remote vault. The entity type is determined by the generic parameter and the
 * path segment used by the REST API.</p>
 *
 * @param <T> entity type extending {@link BaseEntity}
 */
public class VaultClient<T extends BaseEntity> {

    /** Rest client used to communicate with the vault service. */
    protected final RestTemplate restTemplate;

    /** Base URL of the vault service. */
    private String baseUrl;

    private static final String DEFAULT_BASE_URL = "http://localhost:3333";

    /** Class of the entity that this client handles. */
    private final Class<T> entityType;

    /** Path segment for the specific entity type. */
    private final String pathSegment;

    /**
     * Creates a new client using the base URL resolved from the environment or
     * system properties.
     *
     * @param entityType the entity type handled by the client
     */
    public VaultClient(Class<T> entityType) {
        this(entityType, entityType.getAnnotation(Entity.class).name(), loadBaseUrl());
    }

    private VaultClient(Class<T> entityType, String pathSegment, String baseUrl) {
        this.restTemplate = authenticatingRestTemplate();
        this.entityType = entityType;
        this.pathSegment = pathSegment;
        this.baseUrl = baseUrl;
    }

    /**
     * A {@link RestTemplate} that presents the vault API token on every request.
     *
     * <p>The vault serves and accepts spendable proof secrets and the mint's keyset material, and
     * until the 2026-09-05 audit it had no authentication of any kind: {@code GET /vault/proof}
     * returned every stored secret to anyone who could reach the port. The service now requires a
     * bearer token, so this client has to present one.
     *
     * <p>The token is read from {@code VAULT_API_TOKEN} (or {@code vault.api.token}). When it is
     * absent the client sends no header, which fails closed against a secured server: that is the
     * right outcome, because the alternative is a client that silently works against an
     * unsecured one.
     */
    private static RestTemplate authenticatingRestTemplate() {
        RestTemplate template = new RestTemplate();
        String token = loadApiToken();
        if (token == null) {
            log.warn("No vault API token configured (VAULT_API_TOKEN). Requests to a secured "
                    + "vault will be rejected with 401.");
            return template;
        }
        template.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return execution.execute(request, body);
        });
        return template;
    }

    private static String loadApiToken() {
        String env = System.getenv("VAULT_API_TOKEN");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String property = System.getProperty("vault.api.token");
        if (property != null && !property.isBlank()) {
            return property;
        }
        return null;
    }

    /**
     * Creates a new client with an explicit base URL.
     *
     * @param entityType the entity type handled by the client
     * @param baseUrl    base URL of the vault service
     */
    public VaultClient(Class<T> entityType, String baseUrl) {
        this(entityType, entityType.getAnnotation(Entity.class).name(), baseUrl);
    }

    private static String loadBaseUrl() {
        String env = System.getenv("VAULT_BASE_URL");
        if (env != null && !env.isBlank()) {
            return removeTrailingSlash(env);
        }
        String property = System.getProperty("vault.base.url");
        if (property != null && !property.isBlank()) {
            return removeTrailingSlash(property);
        }
        String port = System.getenv("cashu_vault_port");
        if (port != null && !port.isBlank()) {
            return "http://localhost:" + port;
        }
        return DEFAULT_BASE_URL;
    }

    private static String removeTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Stores a new entity in the vault.
     *
     * @param entity entity instance to be stored
     * @return the stored entity returned by the service
     */
    public T store(T entity) {
        log.info("POST {}/vault/{}/", baseUrl, pathSegment);
        T response = restTemplate.postForObject(baseUrl + "/vault/" + pathSegment, entity, entityType);
        log.debug("Stored entity: {}", response != null ? response.getId() : "null");
        return response;
    }

    /**
     * Retrieves an entity by its identifier.
     *
     * @param id identifier of the entity
     * @return the retrieved entity or {@code null} if not found
     */
    public T retrieve(String id) {
        log.info("GET {}/vault/{}/{}", baseUrl, pathSegment, id);
        return restTemplate.getForObject(baseUrl + "/vault/" + pathSegment + "/" + id, entityType);
    }

    /**
     * Archives an entity in the vault.
     *
     * @param id identifier of the entity to archive
     * @return the archived entity returned by the service
     */
    public T archive(String id) {
        log.info("POST {}/vault/{}/archive/{}", baseUrl, pathSegment, id);
        return restTemplate.postForObject(baseUrl + "/vault/" + pathSegment + "/archive/" + id, null, entityType);
    }

    /**
     * Deletes an entity from the vault.
     *
     * @param id identifier of the entity to delete
     */
    public void delete(String id) {
        log.info("DELETE {}/vault/{}/{}", baseUrl, pathSegment, id);
        restTemplate.delete(baseUrl + "/vault/" + pathSegment + "/" + id);
    }

    /**
     * Retrieves all entities of this type from the vault.
     *
     * @return list of all entities, possibly empty
     */
    public List<T> retrieveAll() {
        log.info("GET {}/vault/{}/", baseUrl, pathSegment);
        // Asked for as an array of the concrete entity type rather than as
        // ParameterizedTypeReference<List<T>>: T is erased there, so Jackson was
        // handed the abstract BaseEntity and refused to construct it. entityType
        // is the real class, and an array of it survives erasure.
        @SuppressWarnings("unchecked")
        Class<T[]> arrayType = (Class<T[]>) entityType.arrayType();
        ResponseEntity<T[]> response = restTemplate.exchange(
                baseUrl + "/vault/" + pathSegment,
                HttpMethod.GET,
                null,
                arrayType
        );
        T[] body = response.getBody();
        return body != null ? List.of(body) : Collections.emptyList();
    }
}
