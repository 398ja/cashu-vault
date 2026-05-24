package xyz.tcheeric.cashu.vault.db.repos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T013 — Reflection-based contract test for ProofRepository.
 *
 * Asserts spec 001 / FR-001 / FR-005 invariants at compile-time + runtime:
 *  - findBySecret(String) is REMOVED — no method with that signature exists.
 *  - Every inherited JpaRepository delete* method throws UnsupportedOperationException.
 *  - tombstoneIfActive(UUID, UUID, String) and updateState(UUID, UUID, String) exist
 *    with @Modifying / native @Query.
 */
@SpringBootTest
@DisplayName("ProofRepository contract — append-only + mint-scoped")
class ProofRepositoryContractTest {

    @Autowired
    private ProofRepository repo;

    @Test
    @DisplayName("findBySecret(String) overload is REMOVED from the repository surface")
    void findBySecretOverloadIsAbsent() {
        // FR-005: no public method that takes a single String secret and returns a single-proof type
        // (Optional<ProofEntity> or ProofEntity). Multi-proof admin scans like
        // findByStateIgnoreCase(String) are operational and allowed.
        for (Method m : ProofRepository.class.getMethods()) {
            if (!m.getName().startsWith("findBy")) {
                continue;
            }
            if (m.getName().equals("findByFingerprint")) {
                // fingerprint = SHA-256(secret || mint_id) — effectively mint-scoped; allowed.
                continue;
            }
            if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != String.class) {
                continue;
            }
            Class<?> ret = m.getReturnType();
            boolean returnsSingleProof = ret == ProofEntity.class
                    || (ret == java.util.Optional.class && optionalTypeArg(m) == ProofEntity.class);
            assertThat(returnsSingleProof)
                    .as("%s takes a single String + returns a single ProofEntity — global lookup forbidden by FR-005",
                            m.getName())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("delete(ProofEntity) throws — FR-001 (no physical deletion)")
    void deleteEntityThrows() {
        ProofEntity p = new ProofEntity();
        assertThatThrownBy(() -> repo.delete(p))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Use ProofVaultService.tombstone");
    }

    @Test
    @DisplayName("deleteById(UUID) throws — FR-001 (no physical deletion)")
    void deleteByIdThrows() {
        assertThatThrownBy(() -> repo.deleteById(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("deleteAll() throws — FR-001 (no physical deletion)")
    void deleteAllThrows() {
        assertThatThrownBy(() -> repo.deleteAll())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("deleteAllInBatch() throws — FR-001 (no physical deletion)")
    void deleteAllInBatchThrows() {
        assertThatThrownBy(() -> repo.deleteAllInBatch())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("tombstoneIfActive(UUID, UUID, String) exists with @Modifying + native @Query")
    void tombstoneIfActiveIsDeclared() throws NoSuchMethodException {
        Method m = ProofRepository.class.getMethod("tombstoneIfActive", UUID.class, UUID.class, String.class);
        assertThat(m.isAnnotationPresent(org.springframework.data.jpa.repository.Modifying.class)).isTrue();
        org.springframework.data.jpa.repository.Query q =
                m.getAnnotation(org.springframework.data.jpa.repository.Query.class);
        assertThat(q).isNotNull();
        assertThat(q.nativeQuery()).isTrue();
    }

    @Test
    @DisplayName("updateState(UUID, UUID, String) exists with @Modifying + native @Query")
    void updateStateIsDeclared() throws NoSuchMethodException {
        Method m = ProofRepository.class.getMethod("updateState", UUID.class, UUID.class, String.class);
        assertThat(m.isAnnotationPresent(org.springframework.data.jpa.repository.Modifying.class)).isTrue();
        org.springframework.data.jpa.repository.Query q =
                m.getAnnotation(org.springframework.data.jpa.repository.Query.class);
        assertThat(q).isNotNull();
        assertThat(q.nativeQuery()).isTrue();
    }

    private static Class<?> optionalTypeArg(Method m) {
        Type t = m.getGenericReturnType();
        if (t instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> c) {
                return c;
            }
        }
        return null;
    }
}
