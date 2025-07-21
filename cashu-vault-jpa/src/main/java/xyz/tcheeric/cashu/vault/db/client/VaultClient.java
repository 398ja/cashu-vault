package xyz.tcheeric.cashu.vault.db.client;

import jakarta.persistence.Entity;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import xyz.tcheeric.cashu.vault.db.model.BaseEntity;

import java.util.List;

@Data
@Slf4j
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
        log.info("POST {}/vault/{}/", baseUrl, pathSegment);
        T response = restTemplate.postForObject(baseUrl + "/vault/" + pathSegment, entity, entityType);
        log.debug("Stored entity: {}", response != null ? response.getId() : "null");
        return response;
    }

    public T retrieve(String id) {
        log.info("GET {}/vault/{}/{}", baseUrl, pathSegment, id);
        return restTemplate.getForObject(baseUrl + "/vault/" + pathSegment + "/" + id, entityType);
    }

    public T archive(String id) {
        log.info("POST {}/vault/{}/archive/{}", baseUrl, pathSegment, id);
        return restTemplate.postForObject(baseUrl + "/vault/" + pathSegment + "/archive/" + id, null, entityType);
    }

    public void delete(String id) {
        log.info("DELETE {}/vault/{}/{}", baseUrl, pathSegment, id);
        restTemplate.delete(baseUrl + "/vault/" + pathSegment + "/" + id);
    }

    public List<T> retrieveAll() {
        log.info("GET {}/vault/{}/", baseUrl, pathSegment);
        ResponseEntity<List<T>> response = restTemplate.exchange(
                baseUrl + "/vault/" + pathSegment,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<T>>() {}
        );
        return response.getBody();
    }
}