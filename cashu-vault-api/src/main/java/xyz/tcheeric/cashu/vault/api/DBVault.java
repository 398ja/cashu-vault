package xyz.tcheeric.cashu.vault.api;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.BaseEntity;

@RequiredArgsConstructor
@Data
public abstract class DBVault<T extends BaseEntity> implements Vault<T> {

    private final T entity;
    private final VaultClient<T> client;

    public String retrieve() throws CashuErrorException {
        return retrieve(false);
    }

    public abstract String retrieve(boolean archived) throws CashuErrorException;

    protected abstract T retrieveEntity() throws CashuErrorException;

}
