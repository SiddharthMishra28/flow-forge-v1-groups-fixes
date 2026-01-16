package com.ubs.orkestra.service;

import com.ubs.orkestra.model.PipelineExecution;
import com.ubs.orkestra.repository.PipelineExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PipelineExecutionTxService {

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    // Persist a new PipelineExecution in its own transaction so clients can see it immediately
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PipelineExecution saveNew(PipelineExecution pipelineExecution) {
        return pipelineExecutionRepository.save(pipelineExecution);
    }

    // Persist updates in their own transactions to make progress visible incrementally
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PipelineExecution saveUpdate(PipelineExecution pipelineExecution) {
        return pipelineExecutionRepository.save(pipelineExecution);
    }
}
