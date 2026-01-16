package com.ubs.orkestra.repository;

import com.ubs.orkestra.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByGitlabProjectId(String gitlabProjectId);

    boolean existsByGitlabProjectId(String gitlabProjectId);

    @Modifying
    @Query("UPDATE Application a SET a.tokenValidationLastUpdateDate = :timestamp")
    int updateTokenValidationLastUpdateDateForAll(@Param("timestamp") LocalDateTime timestamp);
}
