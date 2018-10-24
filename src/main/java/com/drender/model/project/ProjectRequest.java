package com.drender.model.project;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectRequest {
    private String id;
    private String software;
    private String source;
    private int startFrame;
    private int endFrame;

    private ProjectAction action;
}
