package com.drender.model.instance;

import com.drender.model.job.Job;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Machine request object to request one or more machines
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InstanceRequest {
    private String cloudAMI;
    private List<Job> jobs;
}
