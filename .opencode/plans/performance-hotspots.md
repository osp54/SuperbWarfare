# Performance Hotspots And Allocation Plan

## Goal

Find the hottest runtime paths and the biggest sources of allocation / RAM churn, then optimize them incrementally with profiling-backed changes.

## Highest-Priority Hotspots

### 1. Vehicle tick loop
- File: `src/main/java/com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity.java`
- Key area: `baseTick()`
- Why hot:
  - Runs every tick for each loaded vehicle
  - Contains movement, turret, weapon, repair, sync, laser, and passenger logic
- Main allocation concerns:
  - Rebuilds `GUN_DATA_MAP`
  - Creates new `HashMap<String, GunData>`
  - Calls `GunData.copy()` per weapon
- Likely impact:
  - High server TPS pressure when many vehicles are loaded

### 2. Auto aim / target acquisition
- File: `src/main/java/com/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity.java`
- Key areas: `baseTick()`, `autoAim()`, `seekNearLivingEntity()`
- Why hot:
  - Runs every tick
  - Scans nearby entities
  - Performs repeated angle checks, smoke checks, `clip(...)`, ballistic math
- Main allocation concerns:
  - `level().getEntities(...)` creates fresh candidate lists
  - `candidates.sort(...)`
  - repeated `Vec3` creation
- Likely impact:
  - Big server cost for autonomous turrets / defense vehicles

### 3. Projectile tick and hit logic
- File: `src/main/java/com/atsuishio/superbwarfare/entity/projectile/ProjectileEntity.java`
- Key area: `tick()`
- Why hot:
  - Runs every tick for every projectile
  - Does block trace, fluid trace, entity search, hit processing, sorting
- Main allocation concerns:
  - new `ArrayList<>` for hit entities
  - entity result materialization
  - sorting even when nearest hit is enough
  - repeated temporary `Vec3`
- Likely impact:
  - Scales badly in projectile-heavy combat

### 4. Shared seek/search utility
- File: `src/main/java/com/atsuishio/superbwarfare/tools/SeekTool.java`
- Why hot:
  - Shared utility used by many systems
  - Costs multiply across lock-on, AI, radar, and targeting
- Main allocation concerns:
  - `StreamSupport.stream(...)`
  - `.stream()`
  - `.toList()`
  - temporary lambda / stream pipeline overhead
  - smoke checks that build lists only to test emptiness
- Likely impact:
  - Broad CPU + allocation overhead across the mod

### 5. Client tick lock-on logic
- File: `src/main/java/com/atsuishio/superbwarfare/event/ClientEventHandler.java`
- Key area: `handleClientTick()`
- Why hot:
  - Runs every client tick
  - Contains weapon lock-on, vehicle seeking, aiming, traces, packet sends
- Main allocation concerns:
  - new `SeekTool.Builder(...)`
  - repeated `Vec3`
  - repeated `ClipContext`
  - repeated entity / block trace calls
- Likely impact:
  - Client FPS/frame pacing issues, especially in combat

### 6. Projectile OBB hit detection mixin
- File: `src/main/java/com/atsuishio/superbwarfare/mixins/ProjectileUtilMixin.java`
- Why hot:
  - Sits in collision / projectile hit path
  - Iterates entities and OBB lists
- Main allocation concerns:
  - repeated `Vec3 -> Vector3d` and `Vector3d -> Vec3` conversions
  - `Optional<Vector3d>`
  - `new Vector3d(...)`
  - repeated `EntityHitResult`
- Likely impact:
  - CPU and churn in all OBB-based projectile checks

## Secondary Hotspots

### 7. Trace helpers
- File: `src/main/java/com/atsuishio/superbwarfare/tools/TraceTool.java`
- Key areas: `getBlocksAlongRay()`, `getEntitiesAlongVector()`
- Concerns:
  - lots of temporary `Vec3`, `BlockPos`, `ArrayList`
  - sorting of hit entities
  - step-based ray marching with `0.1` step may be expensive if used often

### 8. Queue processing in Mod tick
- File: `src/main/java/com/atsuishio/superbwarfare/Mod.java`
- Key areas: server/client tick queue handlers
- Concerns:
  - new `ArrayList<>` every tick
- Priority:
  - low compared to entity/projectile systems
  - easy cleanup later

## Main Allocation Patterns To Attack

### A. Full gun-data map rebuild every tick
- File: `src/main/java/com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity.java`
- Current pattern:
  - create new map
  - copy each `GunData`
  - write entire map back to synced entity data
- Better direction:
  - mutate persistent state in place
  - sync only on change
  - introduce dirty flag / dirty weapon tracking

### B. Streams in hot paths
- File: `src/main/java/com/atsuishio/superbwarfare/tools/SeekTool.java`
- Current pattern:
  - streams for entity filtering and collection
- Better direction:
  - replace with imperative loops in hottest methods
  - avoid materializing lists when only one result is needed

### C. Sort when only minimum is needed
- Files:
  - `AutoAimableEntity.java`
  - `ProjectileEntity.java`
  - `TraceTool.java`
- Better direction:
  - single-pass minimum selection
  - no list sort unless the full ordering is actually needed

### D. Repeated smoke checks with list creation
- File: `src/main/java/com/atsuishio/superbwarfare/tools/SeekTool.java`
- Current pattern:
  - query entities
  - convert to list
  - check `isEmpty()`
- Better direction:
  - early-exit existence check
  - avoid `.stream().toList()`

### E. Repeated vector / shape object churn
- Files:
  - `VehicleEntity.java`
  - `AutoAimableEntity.java`
  - `ProjectileEntity.java`
  - `ClientEventHandler.java`
  - `ProjectileUtilMixin.java`
- Better direction:
  - reduce redundant `new Vec3(...)`
  - avoid repeated conversions in inner loops
  - cache intermediate vectors where safe

## Optimization Pass Order

### Pass 1: Biggest server win
1. [x] Optimize `VehicleEntity.baseTick()`
2. [x] Stop rebuilding `GUN_DATA_MAP` every tick
3. [ ] Introduce dirty-sync strategy for gun state

### Pass 2: Autonomous targeting
1. [x] Rewrite `AutoAimableEntity.seekNearLivingEntity(...)`
2. [x] Remove sort and use single-pass nearest selection
3. [x] Split cheap checks from expensive LOS / smoke / clip checks
4. [x] Reduce expensive reacquire frequency

### Pass 3: Shared seek utility
1. [x] Rewrite hottest `SeekTool` methods without streams
2. [x] Replace smoke checks with no-allocation existence checks
3. [x] Avoid `toList()` when not required

### Pass 4: Projectile scalability
1. [x] Rewrite projectile hit selection to avoid temporary hit lists
2. [x] Keep only closest candidate while scanning
3. [x] Reduce temp objects in `ProjectileEntity.tick()`

### Pass 5: OBB collision path
1. [x] Minimize conversions in `ProjectileUtilMixin`
2. [x] Reuse converted start/end vectors
3. [x] Avoid unnecessary temporary `Vector3d` / `Vec3` creation

### Pass 6: Client-side cleanup
1. [x] Optimize lock-on / seek loops in `ClientEventHandler`
2. [x] Cache LOS / target validation briefly where safe
3. [x] Audit packet send frequency for lock warnings and movement

### Pass 7: Low-risk micro-optimizations
1. [x] `Mod` queue tick temporary list cleanup
2. [x] `TraceTool` ray helpers
3. [x] smaller vector / collection cleanup across utility classes

## Concrete Refactor Ideas

### VehicleEntity
- Keep one persistent gun state map instead of rebuilding it
- Update only modified weapons
- Separate runtime mutable state from sync snapshot state
- Avoid `copy()` in per-tick loop unless strictly required

### AutoAimableEntity
- Query candidates once
- Do a cheap early reject first:
  - self
  - range
  - blacklist
  - water / submerged
  - alive / spectator / creative checks
- Only then do:
  - `canAim`
  - smoke
  - `checkNoClip`
- Track nearest valid target directly without sorting

### SeekTool
- Replace stream-based methods with loops
- Add helper for "has smoke nearby" that returns on first match
- Avoid full-world scans where AABB-limited scans are enough
- Prefer squared distance over `distanceTo(...)`

### ProjectileEntity
- Use closest-hit scan instead of collect-and-sort
- Avoid building intermediate `EntityResult` list if only first hit matters
- Review whether all traces need to run every tick
- Audit motion sync frequency and payload necessity

### ProjectileUtilMixin
- Convert `pStartVec` / `pEndVec` once outside loops
- Avoid creating `new Vector3d(optional.get())` when direct use is enough
- Reduce `Vec3` conversion churn in hit result path
- Re-check whether particle/sound side effects belong in this inner path

### ClientEventHandler
- Reuse computed camera / view vectors in each tick branch
- Avoid rebuilding seek builders when state did not change meaningfully
- Rate-limit expensive target validation if target is unchanged
- Review packet cadence for warning / lock / movement messages

## Measurement Plan

### Profiling targets
- `VehicleEntity.baseTick`
- `AutoAimableEntity.autoAim`
- `AutoAimableEntity.seekNearLivingEntity`
- `ProjectileEntity.tick`
- `SeekTool` hot methods
- `ProjectileUtilMixin.getEntityHitResult`
- `ClientEventHandler.handleClientTick`

### Allocation profiling targets
- `HashMap`
- `ArrayList`
- `Vec3`
- `AABB`
- `Vector3d`
- `EntityResult`
- packet/message allocations

### Stress scenarios
1. Many loaded vehicles, idle and active
2. Many autonomous turrets
3. Projectile spam combat
4. Lock-on weapon use in dense entity scenes
5. OBB-heavy hit detection scene
6. Active radar / station blocks in loaded chunks

### Useful validation metrics
- average tick time by subsystem
- allocations per second
- GC frequency / young-gen churn
- entities scanned per seek
- packets sent per second by type
- projectile count vs tick cost

## Practical Rule For Each Optimization

Before changing code:
- identify one hotspot
- profile it
- change one thing
- re-profile
- keep only wins that help under real combat/load scenarios

## Suggested Execution Checklist

- [x] Optimize `VehicleEntity` gun-state churn
- [x] Optimize `AutoAimableEntity` nearest-target search
- [x] Rewrite hottest `SeekTool` methods without streams
- [x] Optimize `ProjectileEntity` closest-hit flow
- [x] Reduce allocations in `ProjectileUtilMixin`
- [x] Optimize client lock-on tick path
- [x] Clean up smaller utility churn
- [ ] Re-profile and compare before/after
