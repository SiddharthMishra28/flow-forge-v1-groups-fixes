package com.ubs.orkestra.repository;

import com.ubs.orkestra.model.FlowGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FlowGroupRepository extends JpaRepository<FlowGroup, Long> {
}