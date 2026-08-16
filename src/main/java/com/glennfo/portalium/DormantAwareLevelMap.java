package com.glennfo.portalium;

import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DormantAwareLevelMap implements Map<RegistryKey<World>, ServerWorld> {

    private final Map<RegistryKey<World>, ServerWorld> delegate;
    private final Set<RegistryKey<World>> dormantKeys;

    public DormantAwareLevelMap(Map<RegistryKey<World>, ServerWorld> existing, Set<RegistryKey<World>> dormantKeys) {
        this.delegate = existing;
        this.dormantKeys = dormantKeys;
    }

    @Override
    public ServerWorld get(Object key) {
        return delegate.get(key);
    }

    // values() is what MinecraftServer iterates for getAllLevels() — filter dormant dims here
    @Override
    public Collection<ServerWorld> values() {
        return delegate.entrySet().stream()
            .filter(e -> !dormantKeys.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    @Override public Set<Map.Entry<RegistryKey<World>, ServerWorld>> entrySet() { return delegate.entrySet(); }
    @Override public int size()                                                  { return delegate.size(); }
    @Override public boolean isEmpty()                                           { return delegate.isEmpty(); }
    @Override public boolean containsKey(Object key)                            { return delegate.containsKey(key); }
    @Override public boolean containsValue(Object value)                        { return delegate.containsValue(value); }
    @Override public ServerWorld put(RegistryKey<World> key, ServerWorld value) { return delegate.put(key, value); }
    @Override public ServerWorld remove(Object key)                             { return delegate.remove(key); }
    @Override public void putAll(Map<? extends RegistryKey<World>, ? extends ServerWorld> m) { delegate.putAll(m); }
    @Override public void clear()                                               { delegate.clear(); }
    @Override public Set<RegistryKey<World>> keySet()                          { return delegate.keySet(); }
}
