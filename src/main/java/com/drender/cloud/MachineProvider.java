package com.drender.cloud;

import com.drender.model.instance.DRenderInstance;
import com.drender.model.instance.VerifyRequest;
import io.vertx.core.Future;

import java.util.List;

public interface MachineProvider {

    List<DRenderInstance> startMachines(String cloudAMI, int count);
    String machineStatus(DRenderInstance instance);
    void killMachines(List<String> ids);
    Future<Void> restartMachines(List<String> ids, VerifyRequest verifyRequest);
}
