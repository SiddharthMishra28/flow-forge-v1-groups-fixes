package com.ubs.orkestra.repository;

import com.ubs.orkestra.model.FlowExecution;
import com.ubs.orkestra.model.FlowGroup;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.UUID;

public class FlowExecutionSpecification {

    public static Specification<FlowExecution> withFilters(
            UUID executionId,
            Long flowId,
            Long flowGroupId,
            String flowGroupName,
            Integer iteration,
            LocalDateTime fromDate,
            LocalDateTime toDate) {

        return (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();

            // Filter by executionId
            if (executionId != null) {
                predicate = criteriaBuilder.and(predicate,
                    criteriaBuilder.equal(root.get("id"), executionId));
            }

            // Filter by flowId
            if (flowId != null) {
                predicate = criteriaBuilder.and(predicate,
                    criteriaBuilder.equal(root.get("flowId"), flowId));
            }

            // Filter by flowGroupId
            if (flowGroupId != null) {
                predicate = criteriaBuilder.and(predicate,
                    criteriaBuilder.equal(root.get("flowGroupId"), flowGroupId));
            }

            // Filter by iteration
            if (iteration != null) {
                predicate = criteriaBuilder.and(predicate,
                    criteriaBuilder.equal(root.get("iteration"), iteration));
            }

            // Filter by date range
            if (fromDate != null) {
                predicate = criteriaBuilder.and(predicate,
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }

            if (toDate != null) {
                predicate = criteriaBuilder.and(predicate,
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            // Filter by flowGroupName - requires LEFT JOIN
            if (flowGroupName != null && !flowGroupName.trim().isEmpty()) {
                Join<FlowExecution, FlowGroup> flowGroupJoin = root.join("flowGroup", JoinType.LEFT);
                predicate = criteriaBuilder.and(predicate,
                    criteriaBuilder.like(
                        criteriaBuilder.lower(flowGroupJoin.get("flowGroupName")),
                        "%" + flowGroupName.trim().toLowerCase() + "%"
                    ));
            }

            return predicate;
        };
    }
}
