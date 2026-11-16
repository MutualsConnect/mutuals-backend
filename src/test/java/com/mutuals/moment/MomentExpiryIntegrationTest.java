package com.mutuals.moment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ventana de 1 segundo para poder comprobar el vencimiento sin esperar dos minutos. */
@SpringBootTest(properties = {
        "mutuals.moment.window-seconds=1",
        "spring.datasource.url=jdbc:h2:mem:moment-expiry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class MomentExpiryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void anExpiredMomentRejectsPhotosAndLeavesTheStreakUntouched() throws Exception {
        var support = new MomentTestSupport(mockMvc, objectMapper);
        var pair = support.mutualsWithStreak();
        JsonNode started = support.body(support.startMoment(pair.anaToken(), pair.jhanId()));
        long momentId = started.get("id").asLong();
        assertThat(started.get("secondsLeft").asLong()).isLessThanOrEqualTo(1);

        Thread.sleep(1500);

        // Pasó el tiempo: ya no aparece como activo y subir una foto se rechaza.
        mockMvc.perform(get("/api/v1/moments/active").header("Authorization", "Bearer " + pair.jhanToken()))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(support.uploadPhoto(pair.jhanToken(), momentId, "image/png", MomentTestSupport.PNG, null)
                .getResponse().getStatus()).isEqualTo(400);
        mockMvc.perform(get("/api/v1/moments/" + momentId).header("Authorization", "Bearer " + pair.anaToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.secondsLeft").value(0));

        // La racha no se contó.
        JsonNode home = support.body(mockMvc.perform(get("/api/v1/mutuals")
                .header("Authorization", "Bearer " + pair.anaToken())).andReturn());
        assertThat(home.get(0).get("streak").get("countedToday").asBoolean()).isFalse();

        // Y se puede lanzar uno nuevo.
        JsonNode second = support.body(support.startMoment(pair.jhanToken(), pair.anaId()));
        assertThat(second.get("status").asText()).isEqualTo("OPEN");
        assertThat(second.get("id").asLong()).isNotEqualTo(momentId);
    }
}
