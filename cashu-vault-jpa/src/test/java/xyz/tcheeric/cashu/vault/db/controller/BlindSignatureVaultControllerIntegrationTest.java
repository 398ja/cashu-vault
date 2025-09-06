package xyz.tcheeric.cashu.vault.db.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BlindSignatureVaultControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Ensures requesting a non-existing blind signature returns HTTP 404
    @Test
    void retrieveMissingBlindSignatureReturnsNotFound() throws Exception {
        mockMvc.perform(get("/vault/blindsignature/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
