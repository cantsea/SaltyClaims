# Protection coverage

The handlers below are implemented in `ProtectionListener`. Domain/storage/command tests run automatically. Paper event adapters have been compile-checked but have not been verified with live multiplayer clients; this table is an implementation inventory, not a claim of complete exploit coverage.

| Action | Events / adapters | Policy |
| --- | --- | --- |
| Break/place and multi-place | BlockBreakEvent, BlockPlaceEvent/BlockMultiPlaceEvent | block-break/place; all replaced blocks checked |
| Mature crop harvesting/replanting | Break/place plus interaction classification | crop-harvest/replant for wheat, carrots, potatoes, beetroot, nether wart, cocoa |
| Doors, gates, switches, beds | PlayerInteractEvent | door-use, switch-use, bed-use |
| Farmland/egg trampling | PlayerInteractEvent physical | crop-harvest / block-break |
| Containers and utility menus | InventoryOpenEvent, InventoryClickEvent, InventoryDragEvent | container-use / utility-use; both double-chest halves checked; open menus rechecked on edits |
| Buckets and cauldrons | PlayerBucketFill/EmptyEvent, CauldronLevelChangeEvent | bucket-use at affected block |
| Signs and block shearing | SignChangeEvent, PlayerOpenSignEvent, PlayerShearBlockEvent | block-place |
| Lectern books | PlayerTakeLecternBookEvent, PlayerInsertLecternBookEvent | container-use; other lectern interaction currently uses the conservative block-place fallback |
| Entity interaction, armor stands | PlayerInteractEntity/AtEntityEvent, PlayerArmorStandManipulateEvent | animal-interact, decoration-edit, vehicle-use |
| Shearing, leashing, fishing | PlayerShearEntityEvent, PlayerLeash/UnleashEntityEvent, PlayerFishEvent | animal-interact or affected entity edit policy |
| Entity/hanging placement | EntityPlaceEvent, HangingPlaceEvent | vehicle-edit, decoration-edit, or animal-damage; hanging support block also checked |
| Entity/hanging damage | EntityDamageByEntityEvent, HangingBreakEvent | PvP, animal-damage, decoration-edit, vehicle-edit; hostile-monster damage normally allowed |
| Vehicles and mounts | VehicleDamage/Destroy/EnterEvent, EntityMountEvent | vehicle-edit/use |
| Projectiles and potion effects | ProjectileLaunch/HitEvent, PotionSplashEvent, LingeringPotionSplashEvent, AreaEffectCloudApplyEvent | launch provenance persisted; PvP checked at launch/current/target; all potion effects restricted conservatively, including beneficial effects |
| Explosion blocks/entities | EntityExplodeEvent, BlockExplodeEvent, EntityDamageEvent, HangingBreakEvent | per-block explosion-block-damage; explosion-entity-damage for entities |
| Fire and TNT priming | BlockIgnite/Burn/SpreadEvent, TNTPrimeEvent | ignite for attributed player; fire-spread/environment rules otherwise |
| Mob block changes/frost formation | EntityChangeBlockEvent, EntityBlockFormEvent | mob-block-grief / actor block policy |
| Falling blocks | EntitySpawnEvent, EntityChangeBlockEvent | gravity within recorded source region allowed; cross-region changes use mob-block-grief |
| Fluid flow / sponge absorption | BlockFromToEvent, SpongeAbsorbEvent | fluid-crossing on both regions |
| Pistons | BlockPistonExtend/RetractEvent | every moved/broken block and destination; piston-crossing plus actorless break/place when crossing |
| Hoppers/inventory automation | InventoryMoveItemEvent, InventoryPickupItemEvent | hopper-crossing on both regions; transfers crossing inventories also require public container-use |
| Dispensers | BlockDispenseEvent, BlockShearEntityEvent | conservative cross-region target block/entity check; projectile launch provenance handled separately |
| Bonemeal, tree growth, portals | BlockFertilizeEvent, StructureGrowEvent, PortalCreateEvent | all changed locations; autonomous tree growth checked when crossing a region boundary |

## Defaults and boundaries

Root/child identities are distinct boundaries, including stacked children. Crossing piston/fluid/hopper behavior is denied by default between regions; wilderness permits it unless configured otherwise. Same-region automation works without inheriting a player's trust. Permission to cross a boundary does not imply permission to access another container or modify blocks.

Environmental flags do not receive an owner exemption. Staff gameplay bypass applies only to player checks; environment/multi-location checks remain conservative. Other plugins' cancellations are never explicitly reversed.

Unclassified right-click block interactions require block-place authority. This intentionally restricts some uncommon utility interactions until they receive a dedicated classification.

## Live acceptance checklist

- Two players with every trust level; owner and manager delegation; permission changes while menus are open.
- Full-height roots; stacked children; shared X/Z but separated Y; child restriction versus flag inheritance.
- Cross-boundary sticky-piston pulls, slime/honey movement, destroyed blocks, fluid flow, dispenser actions, and hopper minecarts.
- Ranged damage from outside claims, offline projectile owners, lingering potions, tamed pets, explosions, and armor stands/item frames.
- Double containers, multi-block placement, farmland, lecterns, signs, new-version entities/blocks, and interactions from both hands.
- Startup maintenance with a failed database and runtime write failure; disconnect/reconnect during confirmations and bypass.
- Interoperation with plugins that cancel the same events. Administrative world-edit tools can bypass Bukkit gameplay events and are outside this protection contract.

Natural mob spawning, player entry bans, general item pickup/drop restrictions, and a redstone signal isolation system are not implemented. World-specific wilderness booleans in worlds.yml apply even if claiming is disabled. False denies behavior; true allows it. Claim rules are separate and there is no enforced layer.
