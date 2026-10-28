package com.aiworkflow.designer.runtime.simulation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WhatIfResult {

    private String changeDescription;
    private SimulationResult originalResult;
    private SimulationResult modifiedResult;
    private String analysis;
    
    private List<String> recommendations = new ArrayList<>();
    
    private List<String> potentialIssues = new ArrayList<>();

    public WhatIfResult() {
        this.recommendations = new ArrayList<>();
        this.potentialIssues = new ArrayList<>();
    }
}
