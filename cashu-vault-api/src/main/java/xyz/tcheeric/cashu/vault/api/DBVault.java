package xyz.tcheeric.cashu.vault.api;

import lombok.RequiredArgsConstructor;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.BaseEntity;

@RequiredArgsConstructor
public abstract class DBVault<T extends BaseEntity> implements Vault<T> {

    private final VaultClient<T> client;

    @Override
    public T store(T entity) throws CashuErrorException {
        return client.store(entity);
    }

    @Override
    public T retrieve(String id) throws CashuErrorException {
        return client.retrieve(id);
    }

    @Override
    public T archive(String id) throws CashuErrorException {
        return client.archive(id);
    }

    @Override
    public void delete(String id) throws CashuErrorException {
        client.delete(id);
    }

    protected VaultClient<T> getClient() {
        return client;
    }
}
