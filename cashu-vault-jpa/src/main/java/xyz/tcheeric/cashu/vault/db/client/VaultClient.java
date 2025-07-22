package xyz.tcheeric.cashu.vault.db.client;

import jakarta.persistence.Entity;
import lombok.Data;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import xyz.tcheeric.cashu.vault.db.config.VaultBaseProperties;
import xyz.tcheeric.cashu.vault.db.model.BaseEntity;

import java.util.List;

@Data
public class VaultClient<T extends BaseEntity> {
    protected final RestTemplate restTemplate;

    private final String baseUrl;

    private final Class<T> entityType;

    private final String pathSegment;

    public VaultClient(Class<T> entityType, VaultBaseProperties properties) {
        this(
                entityType,
                entityType.getAnnotation(Entity.class).name(),
                properties
        );
    }

    public VaultClient(Class<T> entityType) {
        this(entityType, defaultProperties());
    }

    private VaultClient(Class<T> entityType, String pathSegment, VaultBaseProperties properties) {
        this.restTemplate = new RestTemplate();
        this.entityType = entityType;
        this.pathSegment = pathSegment;
        this.baseUrl = properties.getUrl();
    }

    private static VaultBaseProperties defaultProperties() {
        VaultBaseProperties properties = new VaultBaseProperties();
        String env = System.getenv("VAULT_BASE_URL");
        if (env != null && !env.isBlank()) {
            properties.setUrl(env);
        }
        return properties;
    }

    public T store(T entity) {
        return restTemplate.postForObject(baseUrl + "/vault/" + pathSegment, entity, entityType);
    }

    public T retrieve(String id) {
        return restTemplate.getForObject(baseUrl + "/vault/" + pathSegment + "/" + id, entityType);
    }

    public T archive(String id) {
        return restTemplate.postForObject(baseUrl + "/vault/" + pathSegment + "/archive/" + id, null, entityType);
    }

    public void delete(String id) {
        restTemplate.delete(baseUrl + "/vault/" + pathSegment + "/" + id);
    }

    public List<T> retrieveAll() {
        ResponseEntity<List<T>> response = restTemplate.exchange(
                baseUrl + "/vault/" + pathSegment,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<T>>() {}
        );
        return response.getBody();
    }
}