package com.example.universalattributes;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UniversalAttributesMod implements ModInitializer {
    public static final String MOD_ID = "universalattributes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("universalattributes.json");

    private static ModConfig config;
    private static final SuggestionProvider<ServerCommandSource> SEARCH_SUGGESTIONS = (context, builder) ->
            CommandSource.suggestMatching(buildSearchIndex(), builder);

    @Override
    public void onInitialize() {
        reloadConfig();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> reloadConfig());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                refreshAndApplyToOnlinePlayers(server);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> applyAll(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> applyAll(newPlayer));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity)) {
                return true;
            }
            ModConfig.DamageRule rule = ruleFor(source);
            return rule == null || !rule.immunity;
        });

        CommandRegistrationCallback.EVENT.register(this::registerCommands);
    }

    private void registerCommands(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher,
                                  CommandRegistryAccess registryAccess,
                                  CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("universalattributes")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("reload")
                        .executes(ctx -> {
                            refreshAndApplyToOnlinePlayers(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal("UniversalAttributes config reloaded."), false);
                            return 1;
                        }))
                .then(CommandManager.literal("search")
                        .then(CommandManager.argument("query", StringArgumentType.greedyString())
                                .suggests(SEARCH_SUGGESTIONS)
                                .executes(ctx -> {
                                    String q = StringArgumentType.getString(ctx, "query").toLowerCase();
                                    int matches = 0;
                                    for (Map.Entry<String, ModConfig.AttributeRule> e : config.attributes.entrySet()) {
                                        if (e.getKey().toLowerCase().contains(q) || e.getValue().displayName.toLowerCase().contains(q)) {
                                            ctx.getSource().sendFeedback(() -> Text.literal("[attribute] " + e.getKey() + " -> " + e.getValue().displayName), false);
                                            matches++;
                                        }
                                    }
                                    for (Map.Entry<String, ModConfig.EffectRule> e : config.effects.entrySet()) {
                                        if (e.getKey().toLowerCase().contains(q) || e.getValue().displayName.toLowerCase().contains(q)) {
                                            ctx.getSource().sendFeedback(() -> Text.literal("[effect] " + e.getKey() + " -> " + e.getValue().displayName), false);
                                            matches++;
                                        }
                                    }
                                    for (Map.Entry<String, ModConfig.DamageRule> e : config.damage.entrySet()) {
                                        if (e.getKey().toLowerCase().contains(q) || e.getValue().displayName.toLowerCase().contains(q)) {
                                            ctx.getSource().sendFeedback(() -> Text.literal("[damage] " + e.getKey() + " -> " + e.getValue().displayName), false);
                                            matches++;
                                        }
                                    }
                                    int finalMatches = matches;
                                    ctx.getSource().sendFeedback(() -> Text.literal("Matches: " + finalMatches), false);
                                    return finalMatches;
                                }))));
    }

    private static Iterable<String> buildSearchIndex() {
        List<String> index = new ArrayList<>();
        for (Map.Entry<String, ModConfig.AttributeRule> entry : config.attributes.entrySet()) {
            index.add(entry.getKey());
            if (entry.getValue().displayName != null && !entry.getValue().displayName.isBlank()) {
                index.add(entry.getValue().displayName);
            }
        }
        for (Map.Entry<String, ModConfig.EffectRule> entry : config.effects.entrySet()) {
            index.add(entry.getKey());
            if (entry.getValue().displayName != null && !entry.getValue().displayName.isBlank()) {
                index.add(entry.getValue().displayName);
            }
        }
        for (Map.Entry<String, ModConfig.DamageRule> entry : config.damage.entrySet()) {
            index.add(entry.getKey());
            if (entry.getValue().displayName != null && !entry.getValue().displayName.isBlank()) {
                index.add(entry.getValue().displayName);
            }
        }
        return index;
    }

    private static ModConfig.DamageRule ruleFor(DamageSource source) {
        Identifier id = source.getTypeRegistryEntry().getKey().map(key -> key.getValue()).orElse(null);
        if (id == null) {
            return null;
        }
        return config.damage.get(id.toString());
    }

    public static float scaleIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        ModConfig.DamageRule rule = ruleFor(source);
        if (rule == null) {
            return amount;
        }
        return (float) (amount * (1.0 - Math.max(0.0, Math.min(1.0, rule.resistance))));
    }

    public static void reloadConfig() {
        config = ModConfig.load(CONFIG_PATH);
    }

    public static ModConfig getConfig() {
        return config;
    }

    public static void refreshAndApplyToOnlinePlayers(MinecraftServer server) {
        reloadConfig();
        server.getPlayerManager().getPlayerList().forEach(UniversalAttributesMod::applyAll);
    }

    public static void applyAll(ServerPlayerEntity player) {
        applyAttributes(player);
        applyEffects(player);
        player.setHealth(player.getMaxHealth());
    }

    private static void applyAttributes(ServerPlayerEntity player) {
        for (Map.Entry<String, ModConfig.AttributeRule> entry : config.attributes.entrySet()) {
            ModConfig.AttributeRule rule = entry.getValue();
            if (!rule.enabled) {
                continue;
            }
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null || !Registries.ATTRIBUTE.containsId(id)) {
                continue;
            }
            EntityAttribute attribute = Registries.ATTRIBUTE.get(id);
            EntityAttributeInstance instance = player.getAttributeInstance(attribute);
            if (instance != null) {
                instance.setBaseValue(rule.value);
            } else {
                LOGGER.warn("Could not apply attribute {} immediately for {}. This may require a world rejoin or full restart for core attribute graph changes.", id, player.getEntityName());
            }
        }
    }

    private static void applyEffects(ServerPlayerEntity player) {
        for (Map.Entry<String, ModConfig.EffectRule> entry : config.effects.entrySet()) {
            ModConfig.EffectRule rule = entry.getValue();
            Identifier id = Identifier.tryParse(entry.getKey());
            if (!rule.enabled || id == null || !Registries.STATUS_EFFECT.containsId(id)) {
                continue;
            }
            StatusEffect effect = Registries.STATUS_EFFECT.get(id);
            player.addStatusEffect(new StatusEffectInstance(effect, Integer.MAX_VALUE, rule.amplifier, false, !rule.hideParticles, rule.showIcon));
        }
    }
}
