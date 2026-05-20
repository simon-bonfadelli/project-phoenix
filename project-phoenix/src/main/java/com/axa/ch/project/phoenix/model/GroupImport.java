package com.axa.ch.project.phoenix.model;

import java.util.List;
import java.util.Map;

public class GroupImport {
    private Map<String, List<String>> groupAssignments;

    public Map<String, List<String>> getGroupAssignments() { return groupAssignments; }
    public void setGroupAssignments(Map<String, List<String>> groupAssignments) { this.groupAssignments = groupAssignments; }
}