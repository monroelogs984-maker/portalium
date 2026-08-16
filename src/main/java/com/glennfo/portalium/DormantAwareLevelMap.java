package com.glennfo.portalium;

import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DormantAwareLevelMap implements Map<DimensionType, ServerWorld> {

    private final Map<DimensionType, ServerWorld> delegate;
    private final Set<DimensionType> dormantKeys;

    public DormantAwareLevelMap(Map<DimensionType, ServerWorld> existing, Set<DimensionType> dormantKeys) {
        this.delegate = existing;
        this.dormantKeys = dormantKeys;
    }

    @Override
    public ServerWorld get(Object key) {
        return delegate.get(key);
    }

    // values() is what MinecraftServer iterates when ticking all worlds — filter dormant dims here
    @Override
    public Collection<ServerWorld> values() {
        return delegate.entrySet().stream()
            .filter(e -> !dormantKeys.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    @Override public Set<Map.Entry<DimensionType, ServerWorld>> entrySet()                              { return delegate.entrySet(); }
    @Override public int size()                                                                          { return delegate.size(); }
    @Override public boolean isEmpty()                                                                   { return delegate.isEmpty(); }
    @Override public boolean containsKey(Object key)                                                     { return delegate.containsKey(key); }
    @Override public boolean containsValue(Object value)                                                 { return delegate.containsValue(value); }
    @Override public ServerWorld put(DimensionType key, ServerWorld value)                               { return delegate.put(key, value); }
    @Override public ServerWorld remove(Object key)                                                      { return delegate.remove(key); }
    @Override public void putAll(Map<? extends DimensionType, ? extends ServerWorld> m)                  { delegate.putAll(m); }
    @Override public void clear()                                                                        { delegate.clear(); }
    @Override public Set<DimensionType> keySet()                                                         { return delegate.keySet(); }
}
