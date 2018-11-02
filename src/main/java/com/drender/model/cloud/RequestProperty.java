package com.drender.model.cloud;

import lombok.*;

//@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RequestProperty {
    protected String name; // may remove this field
    protected String region;
}
