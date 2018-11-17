package com.drender;

import com.drender.model.cloud.S3Source;
import com.drender.model.instance.DRenderInstance;
import com.drender.model.job.Job;
import com.drender.model.project.Project;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

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
    // Stores instanceID -> DRenderInstance mapping
    private Map<String, DRenderInstance> instanceMap;
    // Stores instanceID -> [jobIDs] mapping
    private Map<String, List<String>> instanceJobs;
    // Stores instanceID -> heartbeatTimerID mapping
    private Map<String, Long> instanceTimers;
    // Stores jobID -> [frameSet] mapping
    private Map<String, Set<Integer>> jobFrames;

    private Set<String> instanceTerminationList;

    public DRenderDriverModel() {
        projectMap = new HashMap<>();
        jobMap = new HashMap<>();
        projectJobs = new HashMap<>();
        instanceMap = new HashMap<>();
        instanceJobs = new HashMap<>();
        instanceTimers = new HashMap<>();
        jobFrames = new HashMap<>();

        instanceTerminationList = new HashSet<>();
    }

    /**
     * Checks if project is complete.
     * Can't simply check if a project is complete by checking if its corresponding jobs are complete.
     * A job may terminate for some reason and its pending frames will be scheduled in some other job.
     * So, each frame needs to be verified.
     * @param projectID
     * @return
     */
    public boolean isProjectComplete(String projectID) {
        Project project = projectMap.get(projectID);
        int totalFrames = project.getEndFrame() - project.getStartFrame() + 1;
        int[] frames = new int[totalFrames];

        projectJobs.get(projectID)
                .forEach(jobId -> {
                    jobFrames.getOrDefault(jobId, new HashSet<>())
                            .forEach(f -> frames[f-1] = -1);
                });

        return Arrays.stream(frames)
                .allMatch(f ->  f == -1);
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

    public List<Job> getActiveJobs(DRenderInstance instance) {
        return instanceJobs.getOrDefault(instance.getID(), new ArrayList<>())
                .stream()
                .map(jobID -> jobMap.get(jobID))
                .filter(Job::isActive)
                .collect(Collectors.toList());
    }

    public List<String> getActiveJobIds(String projectID) {
        return projectJobs.getOrDefault(projectID, new ArrayList<>())
                .stream()
                .map(id -> jobMap.get(id))
                .filter(Job::isActive)
                .map(Job::getID)
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

    public List<DRenderInstance> getInstances(String projectId) {
        return projectJobs.getOrDefault(projectId, new ArrayList<>())
                .stream()
                .map(jobId -> jobMap.get(jobId).getInstance())
                .filter(instance -> instanceMap.containsKey(instance.getID()))
                .distinct() // an instance could be running multiple jobs
                .collect(Collectors.toList());
    }

    /**
     * Returns the currently active instances for this project.
     * Already terminated instances will be ignored
     * @param projectId
     * @return
     */
    public List<String> getInstanceIds(String projectId) {
        return projectJobs.getOrDefault(projectId, new ArrayList<>())
                .stream()
                .map(jobId -> jobMap.get(jobId).getInstance().getID())
                .filter(instanceId -> instanceMap.containsKey(instanceId))
                .distinct() // an instance could be running multiple jobs
                .collect(Collectors.toList());
    }

    public void updateInstances(List<Job> jobs) {
        Map<DRenderInstance, List<String>> instanceJobIds =
                jobs.stream()
                    .filter(job -> job.getInstance() != null)
                    .collect(groupingBy(Job::getInstance, mapping(Job::getID, toList())));

        // merge these new jobs with existing instance information
        instanceJobIds.forEach((key, value) -> {
            instanceMap.putIfAbsent(key.getID(), key);

            instanceJobs.merge(key.getID(), value,
                (oldList, newList) -> Stream.of(oldList, newList)
                                        .flatMap(Collection::stream)
                                        .collect(Collectors.toList()));
        });
    }

    public Set<Integer> getRenderedFramesForJob(String jobID) {
        return jobFrames.getOrDefault(jobID, new HashSet<>());
    }

    public int getFrameRenderedCount(String jobID) {
        return jobFrames.getOrDefault(jobID, new HashSet<>()).size();
    }

    public Job updateJobInstance(String jobID, DRenderInstance instance) {
        jobMap.get(jobID).setInstance(instance);

        instanceMap.putIfAbsent(instance.getID(), instance);

        instanceJobs.merge(instance.getID(), Collections.singletonList(jobID),
                (oldList, newList) -> Stream.of(oldList, newList)
                                            .flatMap(Collection::stream)
                                            .distinct()
                                            .collect(Collectors.toList()));

        return jobMap.get(jobID);
    }

    public Job updateJobOutputURI(String jobID, S3Source outputURI) {
        jobMap.get(jobID).setOutputURI(outputURI);
        return jobMap.get(jobID);
    }

    public Job updateJobActiveState(String jobID, boolean isActive) {
        jobMap.get(jobID).setActive(isActive);
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

    public void removeInstance(String instanceId) {
        instanceMap.remove(instanceId);
        instanceJobs.remove(instanceId);
        instanceTimers.remove(instanceId);

        instanceTerminationList.remove(instanceId);
    }

    /**
     * Adds the instances to the list of instances to be deleted or being currently deleted.
     * @param instanceIds
     * @return The difference between the current set of instances and the new set
     */
    public List<String> queueInstancesForTermination(List<String> instanceIds) {
        List<String> newInstances =
                    instanceIds
                        .stream()
                        .filter(instanceId -> !instanceTerminationList.contains(instanceId))
                        .collect(Collectors.toList());

        instanceTerminationList.addAll(newInstances);

        return newInstances;
    }

    /**
     * Adds the frame to the set of frames for the job.
     * If all the frames are rendered, the job state is set to inactive.
     * Calls
     * @param jobID
     * @param frame
     */
    public void updateJobFrames(String jobID, int frame) {
        jobFrames.merge(jobID, Collections.singleton(frame),
                (oldSet, newSet) -> Stream.of(oldSet, newSet)
                                        .flatMap(Collection::stream)
                                        .collect(Collectors.toSet()));

        if (isJobComplete(jobID)) {
            updateJobActiveState(jobID, false);
        }
    }

    /**
     * Checks if job is complete.
     * Verifies if all the frames for this job are rendered.
     * @param jobId
     * @return
     */
    public boolean isJobComplete(String jobId) {
        Job job = jobMap.get(jobId);
        int[] frames = new int[job.getEndFrame() - job.getStartFrame() + 1];
        jobFrames.getOrDefault(jobId, new HashSet<>())
                .forEach(f -> frames[f-job.getStartFrame()] = -1);

        return Arrays.stream(frames).allMatch(f -> f == -1);
    }

    /**
     * Finds instances in which all jobs have been rendered
     * @return instanceIDs of matching instances
     */
    public List<String> getInstancesWithCompletedJobs(String projectId) {
        return getInstanceIds(projectId)
                .stream()
                .filter(this::isInstanceJobsComplete)
                .map(instanceId -> instanceMap.get(instanceId).getID())
                .collect(Collectors.toList());
    }

    /**
     * Checks if the jobs running in this instance are complete
     * @param instanceID
     * @return
     */
    public boolean isInstanceJobsComplete(String instanceID) {
        return instanceJobs.getOrDefault(instanceID, new ArrayList<>())
                .stream()
                .noneMatch(jobId -> jobMap.get(jobId).isActive());
    }
}
