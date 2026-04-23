package com.example.universalattributes;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import java.util.function.Consumer;

public class UniversalAttributesMod implements ModInitializer {
    public static final String MOD_ID = "universalattributes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("universalattributes.json");

    private static ModConfig config;
    private static final SuggestionProvider<ServerCommandSource> SEARCH_SUGGESTIONS = (context, builder) ->
            CommandSource.suggestMatching(buildSearchIndex(), builder);
    private static final SuggestionProvider<ServerCommandSource> ATTRIBUTE_ID_SUGGESTIONS = (context, builder) ->
            CommandSource.suggestMatching(config.attributes.keySet(), builder);
    private static final SuggestionProvider<ServerCommandSource> EFFECT_ID_SUGGESTIONS = (context, builder) ->
            CommandSource.suggestMatching(config.effects.keySet(), builder);
    private static final SuggestionProvider<ServerCommandSource> DAMAGE_ID_SUGGESTIONS = (context, builder) ->
            CommandSource.suggestMatching(config.damage.keySet(), builder);
    private static final SuggestionProvider<ServerCommandSource> INTERNAL_ID_SUGGESTIONS = (context, builder) ->
            CommandSource.suggestMatching(config.internals.keySet(), builder);

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
                                })))
                .then(CommandManager.literal("set")
                        .then(CommandManager.literal("attribute")
                                .then(CommandManager.argument("id", StringArgumentType.string()).suggests(ATTRIBUTE_ID_SUGGESTIONS)
                                        .then(CommandManager.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> setAttributeValue(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        DoubleArgumentType.getDouble(ctx, "value"))))))
                        .then(CommandManager.literal("effect")
                                .then(CommandManager.argument("id", StringArgumentType.string()).suggests(EFFECT_ID_SUGGESTIONS)
                                        .then(CommandManager.argument("level", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setEffectLevel(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        IntegerArgumentType.getInteger(ctx, "level"))))))
                        .then(CommandManager.literal("damage_resistance")
                                .then(CommandManager.argument("id", StringArgumentType.string()).suggests(DAMAGE_ID_SUGGESTIONS)
                                        .then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                .executes(ctx -> setDamageResistance(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        DoubleArgumentType.getDouble(ctx, "value"))))))
                        .then(CommandManager.literal("internal_number")
                                .then(CommandManager.argument("id", StringArgumentType.string()).suggests(INTERNAL_ID_SUGGESTIONS)
                                        .then(CommandManager.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> setInternalNumber(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        DoubleArgumentType.getDouble(ctx, "value"))))))
                        .then(CommandManager.literal("internal_boolean")
                                .then(CommandManager.argument("id", StringArgumentType.string()).suggests(INTERNAL_ID_SUGGESTIONS)
                                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setInternalBoolean(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        BoolArgumentType.getBool(ctx, "value")))))))
                .then(CommandManager.literal("enable")
                        .then(CommandManager.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(List.of("attribute", "effect", "damage_immunity", "internal"), builder))
                                .then(CommandManager.argument("id", StringArgumentType.string())
                                        .executes(ctx -> toggleAny(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "type"),
                                                StringArgumentType.getString(ctx, "id"))))))
                .then(CommandManager.literal("disable")
                        .then(CommandManager.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(List.of("attribute", "effect", "damage_immunity", "internal"), builder))
                                .then(CommandManager.argument("id", StringArgumentType.string())
                                        .executes(ctx -> toggleAny(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "type"),
                                                StringArgumentType.getString(ctx, "id"))))))
                .then(CommandManager.literal("list")
                        .then(CommandManager.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(List.of("attributes", "effects", "damage", "internals"), builder))
                                .executes(ctx -> listType(ctx.getSource(), StringArgumentType.getString(ctx, "type"))))));
    }

    private int setAttributeValue(ServerCommandSource source, String id, double value) {
        ModConfig.AttributeRule rule = config.attributes.get(id);
        if (rule == null) return error(source, "Unknown attribute: " + id);
        rule.value = value;
        rule.enabled = true;
        return saveAndApply(source, "Set attribute " + id + " = " + value + " (enabled)");
    }

    private int setEffectLevel(ServerCommandSource source, String id, int level) {
        ModConfig.EffectRule rule = config.effects.get(id);
        if (rule == null) return error(source, "Unknown effect: " + id);
        rule.amplifier = level;
        rule.enabled = true;
        return saveAndApply(source, "Set effect " + id + " level = " + level + " (enabled)");
    }

    private int setDamageResistance(ServerCommandSource source, String id, double value) {
        ModConfig.DamageRule rule = config.damage.get(id);
        if (rule == null) return error(source, "Unknown damage key: " + id);
        rule.resistance = value;
        return saveAndApply(source, "Set damage resistance " + id + " = " + rule.resistance);
    }

    private int setInternalNumber(ServerCommandSource source, String id, double value) {
        ModConfig.InternalRule rule = config.internals.get(id);
        if (rule == null) return error(source, "Unknown internal key: " + id);
        rule.numericValue = value;
        rule.enabled = true;
        return saveAndApply(source, "Set internal number " + id + " = " + value + " (enabled)");
    }

    private int setInternalBoolean(ServerCommandSource source, String id, boolean value) {
        ModConfig.InternalRule rule = config.internals.get(id);
        if (rule == null) return error(source, "Unknown internal key: " + id);
        rule.booleanValue = value;
        rule.enabled = true;
        return saveAndApply(source, "Set internal boolean " + id + " = " + value + " (enabled)");
    }

    private int toggleAny(ServerCommandSource source, String type, String id) {
        return switch (type) {
            case "attribute" -> toggle(source, config.attributes.get(id), id, v -> v.enabled = !v.enabled, v -> v.enabled);
            case "effect" -> toggle(source, config.effects.get(id), id, v -> v.enabled = !v.enabled, v -> v.enabled);
            case "damage_immunity" -> toggle(source, config.damage.get(id), id, v -> v.immunity = !v.immunity, v -> v.immunity);
            case "internal" -> toggle(source, config.internals.get(id), id, v -> v.enabled = !v.enabled, v -> v.enabled);
            default -> error(source, "Unknown type: " + type + ". Use attribute/effect/damage_immunity/internal.");
        };
    }

    private <T> int toggle(ServerCommandSource source, T rule, String id, Consumer<T> toggleAction, java.util.function.Function<T, Boolean> stateGetter) {
        if (rule == null) return error(source, "Unknown key: " + id);
        toggleAction.accept(rule);
        return saveAndApply(source, id + " toggled -> " + stateGetter.apply(rule));
    }

    private int listType(ServerCommandSource source, String type) {
        Iterable<String> keys = switch (type) {
            case "attributes" -> config.attributes.keySet();
            case "effects" -> config.effects.keySet();
            case "damage" -> config.damage.keySet();
            case "internals" -> config.internals.keySet();
            default -> null;
        };
        if (keys == null) return error(source, "Unknown list type: " + type);
        int count = 0;
        for (String key : keys) {
            source.sendFeedback(() -> Text.literal(key), false);
            count++;
        }
        return count;
    }

    private int saveAndApply(ServerCommandSource source, String message) {
        config.save(CONFIG_PATH);
        refreshAndApplyToOnlinePlayers(source.getServer());
        source.sendFeedback(() -> Text.literal(message), false);
        return 1;
    }

    private int error(ServerCommandSource source, String message) {
        source.sendError(Text.literal(message));
        return 0;
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
            if (id == null || !Registries.STATUS_EFFECT.containsId(id)) {
                continue;
            }
            StatusEffect effect = Registries.STATUS_EFFECT.get(id);
            if (!rule.enabled) {
                player.removeStatusEffect(effect);
                continue;
            }
            player.addStatusEffect(new StatusEffectInstance(effect, Integer.MAX_VALUE, rule.amplifier, false, !rule.hideParticles, rule.showIcon));
        }
    }
}
