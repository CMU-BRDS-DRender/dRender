package com.drender.model.cloud;

import lombok.*;

import java.util.List;

//@Builder
@Getter
@Setter
@NoArgsConstructor
//@AllArgsConstructor
public class AWSRequestProperty extends RequestProperty{

    public String machineImageId;
    public String sshKeyName;
    public String securityGroup;
    public String region;

    public AWSRequestProperty(String sshKeyName, String securityGroup, String region, String imageId) {
        this.region =  region;
        this.machineImageId = imageId;
        this.sshKeyName = sshKeyName;
        this.securityGroup = securityGroup;
    }

    public AWSRequestProperty(String sshKeyName, String securityGroup, String region) {
        this.region =  region;
        this.sshKeyName = sshKeyName;
        this.securityGroup = securityGroup;
    }
}
