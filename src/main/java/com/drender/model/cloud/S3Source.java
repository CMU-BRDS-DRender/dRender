package com.drender.model.cloud;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class S3Source {
    private String bucketName;
    private String file;
}
