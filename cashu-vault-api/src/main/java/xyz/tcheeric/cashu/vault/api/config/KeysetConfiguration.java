package xyz.tcheeric.cashu.vault.api.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@Getter
public class KeysetConfiguration implements EntityConfiguration {

    private final MintConfiguration mint;

    @Setter
    private String id;
    private final String unit;

}
