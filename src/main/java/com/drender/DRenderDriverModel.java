package com.drender;

import com.drender.model.cloud.S3Source;
import com.drender.model.instance.DRenderInstance;
import com.drender.model.job.Job;
import com.drender.model.project.Project;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

/**
 * Stores the state of DRenderDriver.
 * Provides methods to access driver data and manages their internal manipulations
 */
public class DRenderDriverModel {

    // Stores projectID ->  project mapping
    private Map<String, Project> projectMap;
    // Stores jobID -> job mapping
    private Map<String, Job> jobMap;
    // Stores projectID -> [jobID] mapping
    private Map<String, List<String>> projectJobs;
    // Stores instanceID -> [jobIDs] mapping
    private Map<DRenderInstance, Set<String>> instanceJobs;
    // Stores instanceID -> heartbeatTimerID mapping
    private Map<String, Long> instanceTimers;
    // Stores jobID -> [frameSet] mapping
    private Map<String, Set<Integer>> jobFrames;

    public DRenderDriverModel() {
        projectMap = new HashMap<>();
        jobMap = new HashMap<>();
        projectJobs = new HashMap<>();
        instanceJobs = new HashMap<>();
        instanceTimers = new HashMap<>();
        jobFrames = new HashMap<>();
    }

    public void addNewProject(Project project) {
        projectMap.put(project.getID(), project);
    }

    public List<Job> getAllJobs(String projectID) {
        return projectJobs.getOrDefault(projectID, new ArrayList<>())
                .stream()
                .map(id -> jobMap.get(id))
                .collect(Collectors.toList());
    }

    public List<String> getAllJobIds(String projectID) {
        return projectJobs.getOrDefault(projectID, new ArrayList<>())
                .stream()
                .map(id -> jobMap.get(id).getID())
                .collect(Collectors.toList());
    }

    public List<Job> getActiveJobs(String projectID) {
        return projectJobs.getOrDefault(projectID, new ArrayList<>())
                .stream()
                .map(id -> jobMap.get(id))
                .filter(Job::isActive)
                .collect(Collectors.toList());
    }

    public int getActiveJobCount(String projectID) {
        return getActiveJobs(projectID).size();
    }

    public void updateProject(Project project) {
        if (projectMap.containsKey(project.getID())) {
            projectMap.put(project.getID(), project);
        }
    }

    public void addNewJobs(List<Job> jobs, String projectID) {
        jobs.forEach(job -> jobMap.put(job.getID(), job));
        projectJobs.merge(projectID,
                jobs.stream().map(Job::getID).collect(Collectors.toList()),
                (oldList, newList) ->
                        Stream.of(oldList, newList)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList())
        );

        updateInstances(jobs);
    }

    public void updateInstances(List<Job> jobs) {
        Map<DRenderInstance, Set<String>> instanceJobIds =
                jobs.stream()
                    .filter(job -> job.getInstance() != null)
                    .collect(groupingBy(Job::getInstance, mapping(Job::getID, toSet())));

        // merge these new jobs with existing instance information
        instanceJobIds.forEach((key, value) -> instanceJobs.merge(key, value,
                (oldSet, newSet) -> Stream.of(oldSet, newSet)
                                        .flatMap(Collection::stream)
                                        .collect(Collectors.toSet())));
    }

    public int getFrameRenderedCount(String jobID) {
        return jobFrames.getOrDefault(jobID, new HashSet<>()).size();
    }

    public Job updateJobInstance(String jobID, DRenderInstance instance) {
        jobMap.get(jobID).setInstance(instance);
        return jobMap.get(jobID);
    }

    public Job updateJobOutputURI(String jobID, S3Source outputURI) {
        jobMap.get(jobID).setOutputURI(outputURI);
        return jobMap.get(jobID);
    }

    public long getInstanceHeartbeatTimer(String instanceID) {
        return instanceTimers.get(instanceID);
    }

    public long updateInstanceTimer(String instanceID, long timerID) {
        instanceTimers.put(instanceID, timerID);
        return instanceTimers.get(instanceID);
    }

    public Project getProject(String projectID) {
        return projectMap.get(projectID);
    }
}
