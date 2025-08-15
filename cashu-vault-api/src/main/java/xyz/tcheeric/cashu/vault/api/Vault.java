package xyz.tcheeric.cashu.vault.api;

import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.model.BaseEntity;

public interface Vault<T extends BaseEntity> {

    T store(T entity) throws CashuErrorException;

    T retrieve(String id) throws CashuErrorException;

    T archive(String id) throws CashuErrorException;

    void delete(String id) throws CashuErrorException;
}
