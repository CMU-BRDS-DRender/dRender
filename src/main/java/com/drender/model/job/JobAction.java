package com.drender.model.job;

import java.io.Serializable;

public enum JobAction implements Serializable {
    START_NEW_MACHINE,
    RESTART_MACHINE,
    KILL_MACHINE,
    HEARTBEAT_CHECK,
    START_JOB
}
