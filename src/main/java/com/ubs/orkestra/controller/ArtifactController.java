package com.ubs.orkestra.controller;

import com.ubs.orkestra.dto.ArtifactCreateDto;
import com.ubs.orkestra.dto.ArtifactDto;
import com.ubs.orkestra.dto.ArtifactPatchDto;
import com.ubs.orkestra.service.ArtifactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/artifacts")
@Tag(name = "Artifacts", description = "Artifact Management API - group FlowExecution runs under a named artifact")
public class ArtifactController {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactController.class);

    @Autowired
    private ArtifactService artifactService;

    @PostMapping("/add")
    @Operation(
            summary = "Create a new artifact",
            description = "Groups one or more FlowExecution run IDs under a named artifact"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Artifact created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or unknown run IDs"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ArtifactDto> createArtifact(@Valid @RequestBody ArtifactCreateDto createDto) {
        logger.info("POST /api/artifacts/add - artifactName={}", createDto.getArtifactName());
        try {
            ArtifactDto created = artifactService.createArtifact(createDto);
            return new ResponseEntity<>(created, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to create artifact: {}", e.getMessage());
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping
    @Operation(summary = "Get all artifacts", description = "Returns a list of all artifacts")
    @ApiResponse(responseCode = "200", description = "List of artifacts")
    public ResponseEntity<List<ArtifactDto>> getAllArtifacts() {
        logger.info("GET /api/artifacts");
        return ResponseEntity.ok(artifactService.getAllArtifacts());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get artifact by ID", description = "Returns a single artifact by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Artifact found"),
            @ApiResponse(responseCode = "404", description = "Artifact not found")
    })
    public ResponseEntity<ArtifactDto> getArtifactById(
            @Parameter(description = "Artifact ID") @PathVariable Long id) {
        logger.info("GET /api/artifacts/{}", id);
        try {
            return ResponseEntity.ok(artifactService.getArtifactById(id));
        } catch (IllegalArgumentException e) {
            logger.error("Artifact not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete artifact by ID", description = "Deletes an artifact by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Artifact deleted"),
            @ApiResponse(responseCode = "404", description = "Artifact not found")
    })
    public ResponseEntity<Void> deleteArtifact(
            @Parameter(description = "Artifact ID") @PathVariable Long id) {
        logger.info("DELETE /api/artifacts/{}", id);
        try {
            artifactService.deleteArtifact(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            logger.error("Artifact not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Patch artifact by ID",
            description = "Modifies an artifact by adding and/or removing FlowExecution run IDs"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Artifact patched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or unknown run IDs to add"),
            @ApiResponse(responseCode = "404", description = "Artifact not found")
    })
    public ResponseEntity<ArtifactDto> patchArtifact(
            @Parameter(description = "Artifact ID") @PathVariable Long id,
            @Valid @RequestBody ArtifactPatchDto patchDto) {
        logger.info("PATCH /api/artifacts/{}", id);
        try {
            ArtifactDto updated = artifactService.patchArtifact(id, patchDto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to patch artifact: {}", e.getMessage());
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().build();
        }
    }
}
