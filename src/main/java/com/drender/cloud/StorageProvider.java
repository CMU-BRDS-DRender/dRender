package com.drender.cloud;

import com.drender.model.cloud.S3Source;

public interface StorageProvider {

    S3Source createStorage(String projectID);
}
