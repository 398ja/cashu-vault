package xyz.tcheeric.cashu.vault.db.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.cashu.common.util.CashuErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KeyVaultControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getKeysByUnitThrowsWhenNoKeys() throws Exception {
        mockMvc.perform(get("/vault/key/unit/TEST"))
                .andExpect(status().isInternalServerError())
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(CashuErrorException.class));
    }
}
