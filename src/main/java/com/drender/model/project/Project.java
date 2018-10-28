package com.drender.model.project;

import com.drender.model.cloud.S3Source;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@Builder
public class Project {
    private String ID;
    private String software;
    private S3Source source;
    private int startFrame;
    private int endFrame;
    private String outputURI;
}
