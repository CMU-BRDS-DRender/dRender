package com.drender.model.cloud;

import lombok.*;

import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
//@AllArgsConstructor
public class AWSRequestProperty extends RequestProperty{

    public String machineImageId;
    public String sshKeyName;
    public String securityGroup;
    public String region;
    public List<String> nameList;


    public AWSRequestProperty(String sshKeyName, String securityGroup , List<String> nameList, String region, String imageId) {
        this.region =  region;
        this.nameList = nameList;
        this.machineImageId = imageId;
        this.sshKeyName = sshKeyName;
        this.securityGroup = securityGroup;
    }

    public AWSRequestProperty(String sshKeyName, String securityGroup , String name, String region, String imageId) {
        this.region =  region;
        this.name = name;
        this.machineImageId = imageId;
        this.sshKeyName = sshKeyName;
        this.securityGroup = securityGroup;
    }
}
