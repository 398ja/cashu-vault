package xyz.tcheeric.cashu.vault.api;

import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.model.BaseEntity;

public interface Vault<T extends BaseEntity> {

    void store() throws CashuErrorException;

    String retrieve() throws CashuErrorException;

    void archive() throws CashuErrorException;

    void delete() throws CashuErrorException;
}
