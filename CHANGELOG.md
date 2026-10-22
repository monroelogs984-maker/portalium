# Changelog

## 2.1.0 (Fabric 1.21.1)
First Fabric release, same behaviour as Portalium 2.1.0 for NeoForge:
- Dimensions with no players go dormant (not ticked) after 8 seconds; the overworld always stays awake.
- Dormant dimensions wake as soon as a player touches a portal, travels there, respawns there or logs in there.
- Dimensions with force-loaded chunks stay awake.
- Dormant dimensions are woken before shutdown so the final save includes them.
- No save on every dimension change and no world time reset (the 2.0.0 bugs).
- Config: `config/portalium.properties` (`unloadDelaySeconds`, 1-300). Requires Fabric API.
