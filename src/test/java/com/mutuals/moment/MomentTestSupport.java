package com.mutuals.moment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ayudantes compartidos por las pruebas de momentos: usuarios, mutuals con racha y subida de fotos. */
class MomentTestSupport {

    static final String PASSWORD = "Str0ng!Pass";

    /** PNG de 1x1 píxel. */
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    record Pair(String anaToken, long anaId, String jhanToken, long jhanId) {
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    MomentTestSupport(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", username + "@mail.com", "username", username,
                                "displayName", username, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("accessToken").asText();
    }

    long myId(String token) throws Exception {
        return body(mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andReturn()).get("id").asLong();
    }

    /** Dos usuarios que se siguen mutuamente y ya tienen una racha activa. */
    Pair mutualsWithStreak() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ana = register("ana" + suffix);
        String jhan = register("jhan" + suffix);
        long anaId = myId(ana);
        long jhanId = myId(jhan);
        mockMvc.perform(post("/api/v1/users/" + jhanId + "/follow").header("Authorization", "Bearer " + ana))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/users/" + anaId + "/follow").header("Authorization", "Bearer " + jhan))
                .andExpect(status().isCreated());
        JsonNode invitation = body(mockMvc.perform(post("/api/v1/streak-invitations")
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("friendId", jhanId))))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(patch("/api/v1/streak-invitations/" + invitation.get("id").asLong())
                        .header("Authorization", "Bearer " + jhan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accept", true))))
                .andExpect(status().isOk());
        return new Pair(ana, anaId, jhan, jhanId);
    }

    MvcResult startMoment(String token, long friendId) throws Exception {
        return mockMvc.perform(post("/api/v1/moments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("friendId", friendId))))
                .andReturn();
    }

    MvcResult uploadPhoto(String token, long momentId, String contentType, byte[] bytes, String caption)
            throws Exception {
        var request = multipart("/api/v1/moments/" + momentId + "/photo")
                .file(new MockMultipartFile("photo", "foto", contentType, bytes))
                .header("Authorization", "Bearer " + token);
        if (caption != null) {
            request.param("caption", caption);
        }
        return mockMvc.perform(request).andReturn();
    }

    JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
