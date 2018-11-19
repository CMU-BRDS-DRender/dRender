package com.drender.cloud;

import com.drender.model.instance.DRenderInstance;
import com.drender.model.cloud.RequestProperty;
import com.drender.model.instance.VerifyRequest;
import io.vertx.core.Future;

import java.util.List;

public interface MachineProvider<T extends RequestProperty> {

    List<DRenderInstance> startMachines(T property, int count);
    String machineStatus(DRenderInstance instance);
    void killMachines(T property, List<String> ids);
    Future<Void> restartMachines(T property, List<String> ids, VerifyRequest verifyRequest);
}
