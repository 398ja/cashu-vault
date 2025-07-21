package xyz.tcheeric.cashu.vault.db.repos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import xyz.tcheeric.cashu.vault.db.config.AuditConfig;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AuditConfig.class)
class KeyRepositoryTests {

    @Autowired
    private KeyRepository keyRepository;
    @Autowired
    private KeySetRepository keySetRepository;
    @Autowired
    private MintRepository mintRepository;

    @Test
    void testFindByPrivateKeyAndUnit() {
        MintEntity mint = new MintEntity();
        mintRepository.save(mint);

        KeySetEntity keySet = new KeySetEntity();
        keySet.setKeySetId("test-set");
        keySet.setUnit("sat");
        keySet.setMint(mint);
        keySetRepository.save(keySet);

        KeyEntity key = new KeyEntity();
        key.setPrivateKey("priv-key");
        key.setAmount(BigInteger.ONE);
        key.setKeySet(keySet);
        keyRepository.save(key);

        Optional<KeyEntity> byPrivate = keyRepository.findByPrivateKey("priv-key");
        assertThat(byPrivate).isPresent();

        Optional<Set<KeyEntity>> byUnit = keyRepository.findByKeySet_UnitIgnoreCase("SAT");
        assertThat(byUnit).isPresent();
        assertThat(byUnit.get()).hasSize(1);

        Optional<Set<KeyEntity>> byKeySet = keyRepository.findByKeySet_Id(keySet.getId());
        assertThat(byKeySet).isPresent();
        assertThat(byKeySet.get()).hasSize(1);
    }
}
