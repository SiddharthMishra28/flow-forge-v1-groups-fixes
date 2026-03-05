package com.ubs.orkestra.repository;

import com.ubs.orkestra.model.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtifactRepository extends JpaRepository<Artifact, Long> {

    List<Artifact> findByArtifactNameContainingIgnoreCase(String artifactName);

    Optional<Artifact> findByArtifactName(String artifactName);
}
