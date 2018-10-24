package com.drender.model.project;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Project {
    private String ID;
    private String software;
    private String source;
    private int startFrame;
    private int endFrame;
    private String outputURI;
}
