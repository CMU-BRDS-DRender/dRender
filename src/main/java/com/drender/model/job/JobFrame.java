package com.drender.model.job;

import com.drender.model.cloud.S3Source;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class JobFrame {
    private String jobID;
    private int frame;
    private S3Source source;
}
