package com.mutuals.moment.controller;

import com.mutuals.common.dto.PageResponse;
import com.mutuals.moment.dto.CreateMomentRequest;
import com.mutuals.moment.dto.MomentResponse;
import com.mutuals.moment.service.MomentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Moments", description = "Momentos de foto: ambos suben una foto en pocos minutos y la racha se confirma sola")
@Validated
@RestController
@RequestMapping("/api/v1/moments")
@RequiredArgsConstructor
public class MomentController {

    private final MomentService momentService;

    @Operation(summary = "Lanzar un momento a un mutual con racha activa; ambos tienen unos minutos para subir su foto")
    @PostMapping
    public ResponseEntity<MomentResponse> start(@Valid @RequestBody CreateMomentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(momentService.start(request.friendId()));
    }

    @Operation(summary = "Momentos abiertos en los que participo (con el tiempo que queda)")
    @GetMapping("/active")
    public ResponseEntity<List<MomentResponse>> active() {
        return ResponseEntity.ok(momentService.active());
    }

    @Operation(summary = "Recuerdos: momentos completados con las dos fotos reveladas")
    @GetMapping("/history")
    public ResponseEntity<PageResponse<MomentResponse>> history(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(momentService.history(pageable));
    }

    @Operation(summary = "Detalle de un momento (la foto del otro se revela al completarse)")
    @GetMapping("/{momentId}")
    public ResponseEntity<MomentResponse> get(@PathVariable Long momentId) {
        return ResponseEntity.ok(momentService.get(momentId));
    }

    @Operation(summary = "Subir mi foto del momento; al llegar la segunda a tiempo se confirma la racha")
    @PostMapping(value = "/{momentId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MomentResponse> submitPhoto(@PathVariable Long momentId,
                                                      @RequestPart MultipartFile photo,
                                                      @RequestParam(required = false) @Size(max = 140) String caption) {
        return ResponseEntity.status(HttpStatus.CREATED).body(momentService.submitPhoto(momentId, photo, caption));
    }
}
