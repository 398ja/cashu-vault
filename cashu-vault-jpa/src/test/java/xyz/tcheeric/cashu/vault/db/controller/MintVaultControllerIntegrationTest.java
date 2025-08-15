package xyz.tcheeric.cashu.vault.db.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MintVaultControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void storeAndRetrieveMint() throws Exception {
        String response = mockMvc.perform(post("/vault/mint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        String id = node.get("id").asText();
        assertThat(id).isNotBlank();

        mockMvc.perform(get("/vault/mint/" + id))
                .andExpect(status().isOk())
                .andExpect(mvcResult -> {
                    JsonNode read = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
                    assertThat(read.get("id").asText()).isEqualTo(id);
                });
    }

    @Test
    void retrieveMissingMintReturnsNotFound() throws Exception {
        mockMvc.perform(get("/vault/mint/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
