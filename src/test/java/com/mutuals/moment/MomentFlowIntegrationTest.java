package com.mutuals.moment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MomentFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MomentTestSupport support;

    @BeforeEach
    void setUp() {
        support = new MomentTestSupport(mockMvc, objectMapper);
    }

    @Test
    void bothPhotosOnTimeConfirmTheStreakWithThePhotoMethod() throws Exception {
        var pair = support.mutualsWithStreak();

        // Ana lanza el momento: queda abierto y con la ventana completa.
        JsonNode started = support.body(support.startMoment(pair.anaToken(), pair.jhanId()));
        long momentId = started.get("id").asLong();
        assertThat(started.get("status").asText()).isEqualTo("OPEN");
        assertThat(started.get("startedByMe").asBoolean()).isTrue();
        assertThat(started.get("secondsLeft").asLong()).isBetween(100L, 120L);
        assertThat(started.get("submittedByMe").asBoolean()).isFalse();

        // Jhan lo ve como pendiente en sus momentos activos.
        mockMvc.perform(get("/api/v1/moments/active").header("Authorization", "Bearer " + pair.jhanToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(momentId))
                .andExpect(jsonPath("$[0].startedByMe").value(false))
                .andExpect(jsonPath("$[0].friend.id").value(pair.anaId()));

        // Jhan sube su foto primero: sigue abierto y la foto de Ana aún no existe.
        JsonNode afterJhan = support.body(support.uploadPhoto(pair.jhanToken(), momentId, "image/png",
                MomentTestSupport.PNG, "Cenando"));
        assertThat(afterJhan.get("status").asText()).isEqualTo("OPEN");
        assertThat(afterJhan.get("submittedByMe").asBoolean()).isTrue();
        assertThat(afterJhan.get("friendSubmitted").asBoolean()).isFalse();

        // Ana ve que su amigo ya subió, pero su foto sigue oculta hasta que ella también suba la suya.
        JsonNode anaView = support.body(mockMvc.perform(get("/api/v1/moments/" + momentId)
                .header("Authorization", "Bearer " + pair.anaToken())).andReturn());
        assertThat(anaView.get("friendSubmitted").asBoolean()).isTrue();
        assertThat(anaView.get("photos").get(0).get("revealed").asBoolean()).isFalse();
        assertThat(anaView.get("photos").get(0).get("photoUrl").isNull()).isTrue();

        // Ana sube la suya: se completa y se revelan las dos.
        JsonNode completed = support.body(support.uploadPhoto(pair.anaToken(), momentId, "image/png",
                MomentTestSupport.PNG, null));
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.get("photos")).hasSize(2);
        completed.get("photos").forEach(photo -> {
            assertThat(photo.get("revealed").asBoolean()).isTrue();
            assertThat(photo.get("photoUrl").asText()).startsWith("/uploads/moments/");
        });

        // La racha del día quedó confirmada sola, con el método PHOTO.
        JsonNode home = support.body(mockMvc.perform(get("/api/v1/mutuals")
                .header("Authorization", "Bearer " + pair.anaToken())).andReturn());
        assertThat(home.get(0).get("streak").get("countedToday").asBoolean()).isTrue();
        assertThat(home.get(0).get("streak").get("currentLength").asInt()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/interactions/timeline").header("Authorization", "Bearer " + pair.jhanToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].method").value("PHOTO"))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));

        // Ya no está activo y aparece en los recuerdos de ambos.
        mockMvc.perform(get("/api/v1/moments/active").header("Authorization", "Bearer " + pair.anaToken()))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/moments/history").header("Authorization", "Bearer " + pair.jhanToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(momentId))
                .andExpect(jsonPath("$.content[0].photos[0].revealed").value(true));
    }

    @Test
    void startingRequiresAnActiveStreakAndOnlyOneOpenMomentAtATime() throws Exception {
        var pair = support.mutualsWithStreak();
        String stranger = support.register("sol" + System.nanoTime());
        long strangerId = support.myId(stranger);

        // Sin racha activa con esa persona no se puede lanzar.
        assertThat(support.startMoment(pair.anaToken(), strangerId).getResponse().getStatus()).isBetween(400, 404);

        JsonNode first = support.body(support.startMoment(pair.anaToken(), pair.jhanId()));
        assertThat(first.get("status").asText()).isEqualTo("OPEN");

        // Un segundo momento abierto con el mismo amigo se rechaza, venga de quien venga.
        assertThat(support.startMoment(pair.anaToken(), pair.jhanId()).getResponse().getStatus()).isEqualTo(409);
        assertThat(support.startMoment(pair.jhanToken(), pair.anaId()).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void aMomentCannotBeStartedOnceTodayIsAlreadyConfirmed() throws Exception {
        var pair = support.mutualsWithStreak();
        long momentId = support.body(support.startMoment(pair.anaToken(), pair.jhanId())).get("id").asLong();
        support.uploadPhoto(pair.anaToken(), momentId, "image/png", MomentTestSupport.PNG, null);
        support.uploadPhoto(pair.jhanToken(), momentId, "image/png", MomentTestSupport.PNG, null);

        assertThat(support.startMoment(pair.jhanToken(), pair.anaId()).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void photoValidationAndAccessRules() throws Exception {
        var pair = support.mutualsWithStreak();
        String outsider = support.register("out" + System.nanoTime());
        long momentId = support.body(support.startMoment(pair.anaToken(), pair.jhanId())).get("id").asLong();

        // Solo imágenes JPEG, PNG o WEBP.
        assertThat(support.uploadPhoto(pair.anaToken(), momentId, "application/pdf", new byte[]{1, 2, 3}, null)
                .getResponse().getStatus()).isEqualTo(400);
        // Una persona ajena al momento no puede subir ni verlo.
        assertThat(support.uploadPhoto(outsider, momentId, "image/png", MomentTestSupport.PNG, null)
                .getResponse().getStatus()).isEqualTo(403);
        mockMvc.perform(get("/api/v1/moments/" + momentId).header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden());

        // Cada persona sube una sola vez.
        assertThat(support.uploadPhoto(pair.anaToken(), momentId, "image/png", MomentTestSupport.PNG, null)
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(support.uploadPhoto(pair.anaToken(), momentId, "image/png", MomentTestSupport.PNG, null)
                .getResponse().getStatus()).isEqualTo(409);

        // Un momento que no existe.
        assertThat(support.uploadPhoto(pair.anaToken(), 987654321L, "image/png", MomentTestSupport.PNG, null)
                .getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void momentsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/moments/active")).andExpect(status().isUnauthorized());
    }
}
