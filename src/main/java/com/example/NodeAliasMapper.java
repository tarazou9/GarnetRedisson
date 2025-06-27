package com.example;
import java.util.HashMap;
import java.util.Map;

public class NodeAliasMapper {
    public static final Map<String, String> HOST_ALIAS_MAP = new HashMap<>();

    static {
        HOST_ALIAS_MAP.put("dc1000000", "10.41.0.54");
        HOST_ALIAS_MAP.put("dc1000001", "10.41.0.51");
        HOST_ALIAS_MAP.put("dc1000002", "10.41.0.53");
        HOST_ALIAS_MAP.put("dc1000003", "10.41.0.56");
        HOST_ALIAS_MAP.put("dc1000004", "10.41.0.52");
        HOST_ALIAS_MAP.put("dc1000005", "10.41.0.55");
    }
}
