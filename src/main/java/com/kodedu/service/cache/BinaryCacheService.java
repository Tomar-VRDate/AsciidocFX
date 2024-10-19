package com.kodedu.service.cache;

import java.awt.image.BufferedImage;

/**
 * Created by usta on 12.06.2016.
 */
public interface BinaryCacheService {
    String label = "core::service::cache::BinaryCache";

    String putBinary(String key, byte[] bytes);

    CacheData getCacheData(String key);

    void putBinary(String key, BufferedImage trimmed);

    boolean hasCache(String key);
}
