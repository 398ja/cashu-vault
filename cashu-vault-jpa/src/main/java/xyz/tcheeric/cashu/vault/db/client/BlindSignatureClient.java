package xyz.tcheeric.cashu.vault.db.client;

import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.vault.db.model.BlindSignatureEntity;

/**
 * REST client providing lookup utilities for {@link BlindSignatureEntity} records.
 */
@Slf4j
public class BlindSignatureClient extends VaultClient<BlindSignatureEntity> {

    /**
     * Creates a new client instance using the default base URL.
     */
    public BlindSignatureClient() {
        super(BlindSignatureEntity.class);
    }

    /**
     * Retrieves a blind signature by its blinded message.
     *
     * @param blindedMessage blinded message value
     * @return matching entity or {@code null} if none exists
     */
    public BlindSignatureEntity getByBlindedMessage(String blindedMessage) {
        log.info("GET {}/vault/blindsignature/message/{}", getBaseUrl(), blindedMessage);
        return restTemplate.getForObject(
                getBaseUrl() + "/vault/blindsignature/message/" + blindedMessage,
                BlindSignatureEntity.class);
    }

    /**
     * Retrieves a blind signature by key set id and blinded message.
     *
     * @param keySetId       external key set id
     * @param blindedMessage blinded message value
     * @return matching entity or {@code null} if none exists
     */
    public BlindSignatureEntity getByKeySetAndBlindedMessage(String keySetId, String blindedMessage) {
        log.info("GET {}/vault/blindsignature/keyset/{}/message/{}", getBaseUrl(), keySetId, blindedMessage);
        return restTemplate.getForObject(
                getBaseUrl() + "/vault/blindsignature/keyset/" + keySetId + "/message/" + blindedMessage,
                BlindSignatureEntity.class);
    }
}
