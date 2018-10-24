package com.drender.cloud;

import com.drender.model.cloud.Instance;
import com.drender.model.cloud.RequestProperty;

public interface CloudProvider<T extends RequestProperty> {

    Instance startMachine(T property);
    String machineStatus(Instance instance);
    boolean killMachine(Instance instance);
}
