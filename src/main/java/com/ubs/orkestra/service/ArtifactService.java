package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.ArtifactCreateDto;
import com.ubs.orkestra.dto.ArtifactDto;
import com.ubs.orkestra.model.Artifact;
import com.ubs.orkestra.repository.ArtifactRepository;
import com.ubs.orkestra.repository.FlowExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ArtifactService {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactService.class);

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private FlowExecutionRepository flowExecutionRepository;

    /**
     * Creates a new artifact grouping a set of FlowExecution run IDs under a name.
     *
     * @param createDto the artifact creation payload
     * @return the persisted artifact as a DTO
     * @throws IllegalArgumentException if any of the provided runIds do not correspond to existing FlowExecutions
     */
    @Transactional
    public ArtifactDto createArtifact(ArtifactCreateDto createDto) {
        logger.info("Creating artifact '{}' with {} run IDs", createDto.getArtifactName(), createDto.getRunIds().size());

        List<UUID> runIds = createDto.getRunIds();

        // Validate that all provided run IDs correspond to existing FlowExecutions
        List<UUID> existingIds = flowExecutionRepository.findAllById(runIds)
                .stream()
                .map(fe -> fe.getId())
                .collect(Collectors.toList());

        List<UUID> missingIds = runIds.stream()
                .filter(id -> !existingIds.contains(id))
                .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "The following runIds do not correspond to existing FlowExecutions: " + missingIds);
        }

        Artifact artifact = new Artifact(createDto.getArtifactName(), runIds);
        Artifact saved = artifactRepository.save(artifact);

        logger.info("Artifact created successfully with id={}", saved.getId());
        return toDto(saved);
    }

    /**
     * Retrieves all artifacts.
     *
     * @return list of all artifact DTOs
     */
    @Transactional(readOnly = true)
    public List<ArtifactDto> getAllArtifacts() {
        return artifactRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a single artifact by its ID.
     *
     * @param id the artifact ID
     * @return the artifact DTO
     * @throws IllegalArgumentException if not found
     */
    @Transactional(readOnly = true)
    public ArtifactDto getArtifactById(Long id) {
        Artifact artifact = artifactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found with id: " + id));
        return toDto(artifact);
    }

    /**
     * Deletes an artifact by its ID.
     *
     * @param id the artifact ID
     * @throws IllegalArgumentException if not found
     */
    @Transactional
    public void deleteArtifact(Long id) {
        if (!artifactRepository.existsById(id)) {
            throw new IllegalArgumentException("Artifact not found with id: " + id);
        }
        artifactRepository.deleteById(id);
        logger.info("Artifact with id={} deleted", id);
    }

    /**
     * Patches an artifact by adding and/or removing FlowExecution run IDs.
     *
     * @param id the artifact ID
     * @param patchDto the patch payload containing runIds to add/remove
     * @return the updated artifact DTO
     * @throws IllegalArgumentException if artifact not found or if any runIds to add don't exist
     */
    @Transactional
    public ArtifactDto patchArtifact(Long id, com.ubs.orkestra.dto.ArtifactPatchDto patchDto) {
        logger.info("Patching artifact with id={}", id);

        Artifact artifact = artifactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found with id: " + id));

        List<UUID> currentRunIds = new java.util.ArrayList<>(artifact.getRunIds());

        // Process additions
        if (patchDto.getAddRunIds() != null && !patchDto.getAddRunIds().isEmpty()) {
            logger.info("Adding {} run IDs to artifact {}", patchDto.getAddRunIds().size(), id);

            // Validate that all run IDs to add exist in FlowExecution
            List<UUID> existingIds = flowExecutionRepository.findAllById(patchDto.getAddRunIds())
                    .stream()
                    .map(fe -> fe.getId())
                    .collect(Collectors.toList());

            List<UUID> missingIds = patchDto.getAddRunIds().stream()
                    .filter(runId -> !existingIds.contains(runId))
                    .collect(Collectors.toList());

            if (!missingIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "The following runIds do not correspond to existing FlowExecutions: " + missingIds);
            }

            // Add run IDs (avoid duplicates)
            for (UUID runId : patchDto.getAddRunIds()) {
                if (!currentRunIds.contains(runId)) {
                    currentRunIds.add(runId);
                }
            }
        }

        // Process removals
        if (patchDto.getRemoveRunIds() != null && !patchDto.getRemoveRunIds().isEmpty()) {
            logger.info("Removing {} run IDs from artifact {}", patchDto.getRemoveRunIds().size(), id);
            currentRunIds.removeAll(patchDto.getRemoveRunIds());
        }

        artifact.setRunIds(currentRunIds);
        Artifact updated = artifactRepository.save(artifact);

        logger.info("Artifact with id={} patched successfully. New runIds count: {}", id, currentRunIds.size());
        return toDto(updated);
    }

    // --- Mapping ---

    private ArtifactDto toDto(Artifact artifact) {
        return new ArtifactDto(
                artifact.getId(),
                artifact.getArtifactName(),
                artifact.getRunIds(),
                artifact.getCreatedAt(),
                artifact.getUpdatedAt()
        );
    }
}
