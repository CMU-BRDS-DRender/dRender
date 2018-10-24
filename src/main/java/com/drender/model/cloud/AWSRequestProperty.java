package com.drender.model.cloud;

import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
//@AllArgsConstructor
public class AWSRequestProperty extends RequestProperty {

    public AWSRequestProperty(String name) {
        this.name = name;
    }
}
