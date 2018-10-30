package com.drender.model.cloud;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class S3Source {
    String bucketName;
    String file;
}
