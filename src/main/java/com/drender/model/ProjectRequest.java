package com.drender.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectRequest {
    private int id;
    private String software;
    private String source;
    private int startFrame;
    private int endFrame;
    private ProjectAction action;
}
