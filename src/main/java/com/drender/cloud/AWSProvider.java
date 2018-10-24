package com.drender.cloud;

import com.drender.model.cloud.AWSRequestProperty;
import com.drender.model.cloud.Instance;

public class AWSProvider implements CloudProvider<AWSRequestProperty>{

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
