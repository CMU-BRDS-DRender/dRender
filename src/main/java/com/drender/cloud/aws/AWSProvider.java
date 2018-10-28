package com.drender.cloud.aws;

import com.drender.cloud.MachineProvider;
import com.drender.model.cloud.AWSRequestProperty;
import com.drender.model.cloud.Instance;

public class AWSProvider implements MachineProvider<AWSRequestProperty> {

    @Override
    public Instance startMachine(AWSRequestProperty property) {
        return new Instance("id", "ip");
    }

    @Override
    public String machineStatus(Instance instance) {
        return null;
    }

    @Override
    public boolean killMachine(Instance instance) {
        return false;
    }
}
