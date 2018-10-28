package com.drender.model.instance;

import com.drender.model.cloud.DrenderInstance;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstanceResponse {
    private String message;
    private List<DrenderInstance> instances;
}
