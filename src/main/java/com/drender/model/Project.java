package com.drender.model;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Project {
    private int ID;
    private String software;
    private String source;
    private int startFrame;
    private int endFrame;
    private String outputURI;
}
