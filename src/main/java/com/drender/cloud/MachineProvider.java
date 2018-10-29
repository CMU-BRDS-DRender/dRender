package com.drender.cloud;

import com.drender.model.instance.DRenderInstance;
import com.drender.model.cloud.RequestProperty;

import java.util.List;

public interface MachineProvider<T extends RequestProperty> {

    List<DRenderInstance> startMachines(T property);
    String machineStatus(DRenderInstance instance);
    boolean killMachine(DRenderInstance instance);
}
