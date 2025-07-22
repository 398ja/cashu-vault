package xyz.tcheeric.cashu.vault.db.client;

import jakarta.persistence.Entity;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import xyz.tcheeric.cashu.vault.db.model.BaseEntity;

import java.util.List;

@Data
public class VaultClient<T extends BaseEntity> {
    protected final RestTemplate restTemplate;

    //@Value("${vault.baseUrl:http://localhost:8080}")
    private String baseUrl = "http://localhost:3333";

    private final Class<T> entityType;

    private final String pathSegment;

    public VaultClient(Class<T> entityType) {
        this(
                entityType,
                entityType.getAnnotation(Entity.class).name()
        );
    }

    private VaultClient(Class<T> entityType, String pathSegment) {
        this.restTemplate = new RestTemplate();
        this.entityType = entityType;
        this.pathSegment = pathSegment;
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