# Сверка с исходным кодом WorldGuard

Автоматический инвентарь имён из `need_migrate`. Упоминание в исходнике указывает место реализации; само по себе оно не доказывает совпадение поведения. Игровые и модульные проверки описаны в MIGRATION.md.

## Флаги: 103

| Флаг оригинала | Нативные файлы |
| --- | --- |
| `allowed-cmds` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java) |
| `block-break` | [BucketMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BucketMixin.java), [FireSpreadMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/FireSpreadMixin.java), [PortalCreationPlan.java](src/main/java/com/sk89q/worldguard/neoforge/protection/PortalCreationPlan.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `block-place` | [BucketMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BucketMixin.java), [EndermanPlaceMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/EndermanPlaceMixin.java), [EntityPlacementMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/EntityPlacementMixin.java), [SpawnEggMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/SpawnEggMixin.java), [PortalCreationPlan.java](src/main/java/com/sk89q/worldguard/neoforge/protection/PortalCreationPlan.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `block-trampling` | [TramplingMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/TramplingMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `blocked-cmds` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java) |
| `breeze-charge-explosion` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `build` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java), [BonemealMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BonemealMixin.java), [BrushMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BrushMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java), [RegionQuery.java](src/main/java/com/sk89q/worldguard/neoforge/region/RegionQuery.java), [SignChestMixin.java](src/main/java/com/sk89q/worldguard/neoforge/rules/mixin/SignChestMixin.java) |
| `chest-access` | [EntityContainerMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/EntityContainerMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `chorus-fruit-teleport` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `copper-fade` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `coral-fade` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `creeper-explosion` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `crop-growth` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [BonemealMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BonemealMixin.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `damage-animals` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `deny-message` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `deny-spawn` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `enderdragon-block-damage` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `enderman-grief` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `enderpearl` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `entity-item-frame-destroy` | [HangingPhysicsMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/HangingPhysicsMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `entity-painting-destroy` | [HangingPhysicsMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/HangingPhysicsMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `entry` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `entry-deny-message` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `exit` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `exit-deny-message` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `exit-override` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `exit-via-teleport` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `exp-drops` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ExperienceMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/ExperienceMixin.java), [ExperienceOrbMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/ExperienceOrbMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `fall-damage` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `farewell` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `farewell-title` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `feed-amount` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `feed-delay` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `feed-max-hunger` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `feed-min-hunger` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `fire-spread` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java), [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [FireSpreadMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/FireSpreadMixin.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java), [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `firework-damage` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `frosted-ice-form` | [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `frosted-ice-melt` | [IceMeltMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/IceMeltMixin.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `game-mode` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `ghast-fireball` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `grass-growth` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `greeting` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `greeting-title` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `heal-amount` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `heal-delay` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `heal-max-health` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `heal-min-health` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `ice-form` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `ice-melt` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [IceMeltMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/IceMeltMixin.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `interact` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `invincible` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java), [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `item-drop` | [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `item-frame-rotation` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `item-pickup` | [HopperMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/HopperMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `lava-fire` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java), [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [LavaFireMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/LavaFireMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `lava-flow` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java), [FluidMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/FluidMixin.java) |
| `lava-harden` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `leaf-decay` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `lighter` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java), [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [BlockInteractionMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BlockInteractionMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `lightning` | [LightningMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/LightningMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `mob-damage` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `mob-spawning` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `moisture-change` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `mushroom-growth` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `mycelium-spread` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `natural-health-regen` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [FoodDataMixin.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/mixin/FoodDataMixin.java) |
| `natural-hunger-drain` | [FoodDataMixin.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/mixin/FoodDataMixin.java) |
| `nonplayer-protection-domains` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java), [RegionQuery.java](src/main/java/com/sk89q/worldguard/neoforge/region/RegionQuery.java) |
| `notify-enter` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `notify-leave` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `other-explosion` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `passthrough` | [RegionQuery.java](src/main/java/com/sk89q/worldguard/neoforge/region/RegionQuery.java) |
| `pistons` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `potion-splash` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `pvp` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `ravager-grief` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `receive-chat` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java), [ChatRecipientMixin.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/mixin/ChatRecipientMixin.java), [PacketListenerMixin.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/mixin/PacketListenerMixin.java) |
| `respawn-anchors` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `ride` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `rock-growth` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `sculk-growth` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [SculkMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/SculkMixin.java) |
| `send-chat` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java) |
| `sleep` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `snow-fall` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `snow-melt` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `snowman-trails` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `soil-dry` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `spawn` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java), [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `teleport` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `teleport-message` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `time-lock` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `tnt` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [BlockInteractionMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BlockInteractionMixin.java), [TntMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/TntMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `use` | [BlockInteractionMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BlockInteractionMixin.java), [BoatPlacementMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BoatPlacementMixin.java), [BucketMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BucketMixin.java), [ItemSpawnCauseMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/ItemSpawnCauseMixin.java), [SpawnEggMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/SpawnEggMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `use-anvil` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `use-dripleaf` | [DripleafMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/DripleafMixin.java) |
| `vehicle-destroy` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `vehicle-place` | [BoatPlacementMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BoatPlacementMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `vine-growth` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `water-flow` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java), [FluidMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/FluidMixin.java) |
| `weather-lock` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `wind-charge-burst` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `wither-damage` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java), [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |

## Настройки: 156

| Ключ оригинала | Нативные файлы |
| --- | --- |
| `auto-invincible` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java) |
| `auto-invincible-group` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java) |
| `auto-invincible-permission` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java) |
| `auto-no-drowning-group` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java) |
| `blacklist.logging.console.enable` | [BlacklistService.java](src/main/java/com/sk89q/worldguard/neoforge/rules/BlacklistService.java) |
| `blacklist.logging.database.dsn` | [BlacklistService.java](src/main/java/com/sk89q/worldguard/neoforge/rules/BlacklistService.java) |
| `blacklist.logging.database.enable` | [BlacklistService.java](src/main/java/com/sk89q/worldguard/neoforge/rules/BlacklistService.java) |
| `blacklist.logging.database.pass` | [BlacklistService.java](src/main/java/com/sk89q/worldguard/neoforge/rules/BlacklistService.java) |
| `blacklist.logging.database.table` | [BlacklistService.java](src/main/java/com/sk89q/worldguard/neoforge/rules/BlacklistService.java) |
| `blacklist.logging.database.user` | [BlacklistService.java](src/main/java/com/sk89q/worldguard/neoforge/rules/BlacklistService.java) |
| `blacklist.logging.file.enable` | [BlacklistService.java](src/main/java/com/sk89q/worldguard/neoforge/rules/BlacklistService.java) |
| `blacklist.logging.file.open-files` | Файлы открываются и закрываются при каждой записи; одновременно удерживается один файловый дескриптор, кеш открытых журналов Bukkit не используется. |
| `blacklist.logging.file.path` | [BlacklistService.java](src/main/java/com/sk89q/worldguard/neoforge/rules/BlacklistService.java) |
| `blacklist.use-as-whitelist` | [BlacklistService.java](src/main/java/com/sk89q/worldguard/neoforge/rules/BlacklistService.java) |
| `build-permission-nodes.deny-message` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `build-permission-nodes.enable` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `chest-protection.disable-off-check` | [SignChestProtection.java](src/main/java/com/sk89q/worldguard/neoforge/rules/SignChestProtection.java) |
| `chest-protection.enable` | [SignChestProtection.java](src/main/java/com/sk89q/worldguard/neoforge/rules/SignChestProtection.java) |
| `crops.disable-creature-trampling` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `crops.disable-player-trampling` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `default.disable-health-regain` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `default.pumpkin-scuba` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `disable-permission-cache` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `dynamics.disable-copper-block-fade` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-coral-block-fade` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-crop-growth` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-grass-growth` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-ice-formation` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-ice-melting` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-lava-harden` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-leaf-decay` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-mushroom-spread` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-mycelium-spread` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-rock-growth` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-sculk-growth` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-snow-formation` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-snow-melting` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-soil-dehydration` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-soil-moisture-change` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.disable-vine-growth` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `dynamics.snow-fall-blocks` | [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `event-handling.block-entity-spawns-with-untraceable-cause` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `event-handling.break-hoppers-on-denied-move` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `event-handling.emit-block-use-at-feet` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `event-handling.ignore-hopper-item-move-events` | [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `event-handling.interaction-whitelist` | [BlockInteractionMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/BlockInteractionMixin.java), [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `fire.disable-all-fire-spread` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `fire.disable-fire-spread-blocks` | [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `fire.disable-lava-fire-spread` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `fire.lava-spread-blocks` | [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `gameplay.block-potions` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java), [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `gameplay.block-potions-overly-reliably` | [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `gameplay.disable-conduit-effects` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `host-keys` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [HostKeyService.java](src/main/java/com/sk89q/worldguard/neoforge/rules/HostKeyService.java) |
| `ignition.block-lighter` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `ignition.block-tnt` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `ignition.block-tnt-block-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.allow-tamed-spawns` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `mobs.anti-wolf-dumbness` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-above-ground-slimes` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-armor-stand-destroy` | [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java), [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-creature-spawn` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `mobs.block-creeper-block-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-creeper-explosions` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `mobs.block-enderdragon-block-damage` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java), [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-enderdragon-portal-creation` | [DragonPortalMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/DragonPortalMixin.java) |
| `mobs.block-fireball-block-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-fireball-explosions` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `mobs.block-item-frame-destroy` | [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `mobs.block-other-explosions` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-painting-destroy` | [ProtectionHooks.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionHooks.java) |
| `mobs.block-plugin-spawning` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `mobs.block-vehicle-entry` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-windcharge-explosions` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-wither-block-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-wither-explosions` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `mobs.block-wither-skull-block-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-wither-skull-explosions` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.block-zombie-door-destruction` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.disable-enderman-griefing` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `mobs.disable-snowman-trails` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `op-permissions` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `physics.allow-portal-anywhere` | [PortalPhysicsMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/PortalPhysicsMixin.java) |
| `physics.disable-water-damage-blocks` | [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `physics.no-physics-gravel` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java), [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `physics.no-physics-sand` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java), [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `physics.vine-like-rope-ladders` | [LadderPhysicsMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/LadderPhysicsMixin.java) |
| `player-damage.disable-contact-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `player-damage.disable-death-messages` | [DeathMessageMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/DeathMessageMixin.java) |
| `player-damage.disable-drowning-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `player-damage.disable-explosion-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `player-damage.disable-fall-damage` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `player-damage.disable-fire-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `player-damage.disable-lava-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `player-damage.disable-lightning-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `player-damage.disable-mob-damage` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `player-damage.disable-suffocation-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `player-damage.disable-void-damage` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `player-damage.reset-fall-on-void-teleport` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `player-damage.teleport-on-suffocation` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `player-damage.teleport-on-void-falling` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `protection.disable-xp-orb-drops` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `protection.item-durability` | [ToolDurabilityMixin.java](src/main/java/com/sk89q/worldguard/neoforge/rules/mixin/ToolDurabilityMixin.java) |
| `protection.remove-infinite-stacks` | Отрицательное количество означает пустой ItemStack в Minecraft 1.21.1; старые бесконечные стеки не существуют. |
| `protection.use-max-priority-association` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `regions.announce-bypass-status` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java) |
| `regions.cancel-chat-without-recipients` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java) |
| `regions.claim-only-inside-existing-regions` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `regions.disable-bypass-by-default` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `regions.enable` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `regions.explosion-flags-block-entity-damage` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `regions.fake-player-build-override` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `regions.high-frequency-flags` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `regions.invincibility-removes-mobs` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `regions.location-flags-only-inside-regions` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `regions.max-claim-volume` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `regions.max-region-count-per-player.` | Это префикс динамических ключей групп, а не отдельная настройка. Реализован через карту regions.max-region-count-per-player и RegionGroupEvent. |
| `regions.max-region-count-per-player.default` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `regions.nether-portal-protection` | [PortalCreationPlan.java](src/main/java/com/sk89q/worldguard/neoforge/protection/PortalCreationPlan.java) |
| `regions.protect-against-liquid-flow` | [FluidMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/FluidMixin.java) |
| `regions.set-parent-on-claim` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `regions.sql.dsn` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `regions.sql.password` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `regions.sql.table-prefix` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `regions.sql.use` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java), [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `regions.sql.username` | [WorldGuardConfig.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardConfig.java) |
| `regions.titles-always-use-default-times` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `regions.use-creature-spawn-event` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `regions.use-paper-entity-origin` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `regions.use-pre-creature-spawn-event` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `regions.uuid-migration.keep-names-that-lack-uuids` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `regions.uuid-migration.perform-on-next-start` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `regions.wand` | [ProtectionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/protection/ProtectionEvents.java) |
| `security.block-in-game-op-command` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java) |
| `security.deop-everyone-on-join` | [SessionEvents.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/SessionEvents.java) |
| `security.host-keys-allow-forge-clients` | [HostKeyService.java](src/main/java/com/sk89q/worldguard/neoforge/rules/HostKeyService.java) |
| `simulation.sponge.enable` | [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `simulation.sponge.radius` | [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `simulation.sponge.redstone` | [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `sniffer-egg.disable-creature-trampling` | Яйцо нюхача в Minecraft 1.21.1 не разрушается от наступания существ. |
| `sniffer-egg.disable-player-trampling` | Яйцо нюхача в Minecraft 1.21.1 не разрушается от наступания игроков. |
| `summary-on-start` | [WorldGuardMod.java](src/main/java/com/sk89q/worldguard/neoforge/WorldGuardMod.java) |
| `turtle-egg.disable-creature-trampling` | [TramplingMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/TramplingMixin.java) |
| `turtle-egg.disable-player-trampling` | [TramplingMixin.java](src/main/java/com/sk89q/worldguard/neoforge/mixin/TramplingMixin.java) |
| `use-particle-effects` | [ProtectionService.java](src/main/java/com/sk89q/worldguard/neoforge/ProtectionService.java) |
| `use-player-move-event` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `use-player-teleports` | [PlayerSessions.java](src/main/java/com/sk89q/worldguard/neoforge/sessions/PlayerSessions.java) |
| `weather.always-raining` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `weather.always-thundering` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `weather.disable-lightning-strike-fire` | [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |
| `weather.disable-pig-zombification` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `weather.disable-powered-creepers` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `weather.disable-thunderstorm` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `weather.disable-villager-witchification` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `weather.disable-weather` | [WorldRulesEvents.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesEvents.java) |
| `weather.prevent-lightning-strike-blocks` | [WorldRulesHooks.java](src/main/java/com/sk89q/worldguard/neoforge/rules/WorldRulesHooks.java) |

## Имена и псевдонимы команд: 81

Учитывается регистрация и адаптер старого синтаксиса. Формат вывода и платформенные отличия указаны в MIGRATION.md.

| Имя оригинала | Нативные файлы |
| --- | --- |
| `;` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java) |
| `addmem` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `addmember` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `addowner` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `allowfire` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java), [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java) |
| `am` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `ao` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `bypass` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `claim` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `clearstates` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `create` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `d` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `debug` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `def` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `define` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `del` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `delete` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `f` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `flag` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `flags` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `flushstates` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `god` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java), [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java) |
| `halt-activity` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java), [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java) |
| `haltactivity` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java), [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java) |
| `heal` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java), [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java) |
| `i` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `info` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `list` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `load` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `locate` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java) |
| `migratedb` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `migrateheights` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `migrateuuid` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `move` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `par` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `parent` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `pri` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `priority` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `profile` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java) |
| `queue` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `redefine` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `region` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `regions` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `reload` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `rem` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `remmem` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `remmember` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `remove` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `removemem` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `removemember` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `removeowner` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `remowner` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `report` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `rg` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `rm` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `ro` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `running` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `s` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `save` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `sel` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `select` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `setparent` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `setpriority` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `slay` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java), [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java) |
| `stack` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java) |
| `stopfire` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java), [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java) |
| `stoplag` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java), [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java) |
| `stopprofile` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java) |
| `teleport` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `testbreak` | [DebugCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/DebugCommands.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `testdamage` | [DebugCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/DebugCommands.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `testinteract` | [DebugCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/DebugCommands.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `testplace` | [DebugCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/DebugCommands.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `toggle-bypass` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `tp` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `ungod` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java), [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java) |
| `update` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `version` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `wg` | [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `worldguard` | [AdministrationCommands.java](src/main/java/com/sk89q/worldguard/neoforge/admin/AdministrationCommands.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |
| `write` | [LegacyCommandSyntax.java](src/main/java/com/sk89q/worldguard/neoforge/command/LegacyCommandSyntax.java), [RegionCommands.java](src/main/java/com/sk89q/worldguard/neoforge/command/RegionCommands.java) |

## Семейства обработчиков Bukkit

Это карта замены платформенного слоя, а не доказательство эквивалентности каждого события Bukkit и NeoForge.

| Исходный обработчик | Замена |
| --- | --- |
| `BlacklistListener.java` | rules/BlacklistService.java, rules/WorldRulesEvents.java и mixin-хуки предметов: blacklist и действия журналирования/уведомления/наказания. |
| `BlockedPotionsListener.java` | rules/WorldRulesHooks.java, rules/WorldRulesEvents.java, SplashPotionMixin и LingeringPotionMixin: предметы, эффекты, override и reliable blocking. |
| `BuildPermissionListener.java` | ProtectionService.buildPermission, protection/ProtectionEvents.java и RegionPermissionEvent: оба порядка узлов прав и отдельные виды действий. |
| `ChestProtectionListener.java` | rules/SignChestProtection.java, SignChestMixin, HopperMixin, ContainerDropMixin: таблички, контейнеры, обе половины сундука и автоматизация. |
| `DebuggingListener.java` | admin/DebugCommands.java: симуляция событий NeoForge и конечный результат. Приписывание отмены Bukkit-плагинам отсутствует на этой платформе. |
| `EventAbstractionListener.java` | protection/ProtectionEvents.java, ProtectionHooks.java и mixin/: нативные события и причины изменений; слоя Bukkit-событий нет. |
| `InvincibilityListener.java` | rules/WorldRulesEvents.java, protection/ProtectionEvents.java и ProtectionService: god, урон, воздух и защита от эффектов. |
| `PlayerModesListener.java` | sessions/SessionEvents.java и RegionGroupEvent/RegionPermissionEvent: автоматические god/no-drowning. |
| `PlayerMoveListener.java` | sessions/PlayerSessions.java и sessions/mixin/: движения, порталы, телепортации и восстановления режимов. |
| `RegionFlagsListener.java` | sessions/SessionEvents.java, PlayerSessions.java и PacketListenerMixin/ChatRecipientMixin: сообщения, чат, команды, вход и выход. |
| `RegionProtectionListener.java` | protection/ProtectionEvents.java и mixin/: проверки блоков, предметов, сущностей, взрывов, механизмов и членства. |
| `WorldGuardBlockListener.java` | protection/ProtectionEvents.java, ProtectionHooks.java, rules/WorldRulesHooks.java и mixin/: глобальные и региональные изменения блоков. |
| `WorldGuardCommandBookListener.java` | Двоичная интеграция с CommandBook является Bukkit-интеграцией. Доступны /rg info, /wg report и публичный нативный API для модов. |
| `WorldGuardEntityListener.java` | protection/ProtectionEvents.java, rules/WorldRulesEvents.java и mixin/: спавн, урон, грифинг, опыт, порталы, сущности. |
| `WorldGuardHangingListener.java` | DecorationDamageMixin, HangingPhysicsMixin, EntityPlacementMixin и ProtectionEvents: рамки, картины и опоры. |
| `WorldGuardPlayerListener.java` | protection/ProtectionEvents.java, rules/WorldRulesEvents.java, SessionEvents.java и mixin/: игроки, предметы, сон, безопасность, вход. |
| `WorldGuardServerListener.java` | WorldGuardMod.java и события API: жизненный цикл NeoForge и регистрация нативных расширений; загрузки Bukkit-плагинов нет. |
| `WorldGuardVehicleListener.java` | BoatPlacementMixin, DecorationDamageMixin и ProtectionEvents: размещение, посадка и разрушение транспорта. |
| `WorldGuardWeatherListener.java` | WorldRulesEvents.java, WeatherMixin и LightningMixin: погода, молния и превращения существ. |
| `WorldGuardWorldListener.java` | WorldGuardMod.java, SessionEvents.java, WorldRulesEvents.java: загрузка хранилищ, погода и halt-activity при появлении сущностей. |
| `WorldRulesListener.java` | rules/WorldRulesHooks.java и mixin/: физика, жидкости, губки, рост и естественные изменения. |
