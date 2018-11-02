package com.drender.model.instance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InstanceHeartbeat {
    private DRenderInstance instance;
    private DRenderInstanceAction action;
}
