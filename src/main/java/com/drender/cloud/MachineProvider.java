package com.drender.cloud;

import com.drender.model.cloud.DrenderInstance;
import com.drender.model.cloud.RequestProperty;

import java.util.List;

public interface MachineProvider<T extends RequestProperty> {

    List<DrenderInstance> startMachines(T property);
    String machineStatus(DrenderInstance instance);
    boolean killMachine(DrenderInstance instance);
}
