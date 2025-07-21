package xyz.tcheeric.cashu.vault.api;

import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.config.EntityConfiguration;

public interface Vault<T extends EntityConfiguration> {

    void store() throws CashuErrorException;

    String retrieve() throws CashuErrorException;

    void archive() throws CashuErrorException;

    void delete() throws CashuErrorException;
}
