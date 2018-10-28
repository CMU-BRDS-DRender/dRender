package com.drender.cloud;
import com.drender.model.cloud.RequestProperty;
public interface StorageProvider<T extends RequestProperty> {

    String createStorage(T property);

}
