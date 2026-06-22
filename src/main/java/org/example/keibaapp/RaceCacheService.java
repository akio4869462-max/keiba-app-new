package org.example.keibaapp;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RaceCacheService {

    private final Map<String, HorseDetailInfo> horseDetailCache
            = new HashMap<>();

    public HorseDetailInfo getHorseDetail(String key) {
        return horseDetailCache.get(key);
    }

    public void putHorseDetail(String key,
                               HorseDetailInfo detail) {
        horseDetailCache.put(key, detail);
    }
}