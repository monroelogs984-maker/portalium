package com.glennfo.portalium;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DormantAwareLevelMap implements Map<ResourceKey<Level>, ServerLevel> {

    private final Map<ResourceKey<Level>, ServerLevel> delegate;
    private final Set<ResourceKey<Level>> dormantKeys;

    public DormantAwareLevelMap(Map<ResourceKey<Level>, ServerLevel> existing, Set<ResourceKey<Level>> dormantKeys) {
        this.delegate = existing;
        this.dormantKeys = dormantKeys;
    }

    @Override
    public ServerLevel get(Object key) {
        return delegate.get(key);
    }

    // values() is what MinecraftServer.getAllLevels() iterates — filter out dormant dims here
    @Override
    public Collection<ServerLevel> values() {
        return delegate.entrySet().stream()
            .filter(e -> !dormantKeys.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    @Override public Set<Map.Entry<ResourceKey<Level>, ServerLevel>> entrySet() { return delegate.entrySet(); }
    @Override public int size()                                                  { return delegate.size(); }
    @Override public boolean isEmpty()                                           { return delegate.isEmpty(); }
    @Override public boolean containsKey(Object key)                            { return delegate.containsKey(key); }
    @Override public boolean containsValue(Object value)                        { return delegate.containsValue(value); }
    @Override public ServerLevel put(ResourceKey<Level> key, ServerLevel value) { return delegate.put(key, value); }
    @Override public ServerLevel remove(Object key)                             { return delegate.remove(key); }
    @Override public void putAll(Map<? extends ResourceKey<Level>, ? extends ServerLevel> m) { delegate.putAll(m); }
    @Override public void clear()                                               { delegate.clear(); }
    @Override public Set<ResourceKey<Level>> keySet()                          { return delegate.keySet(); }
}
