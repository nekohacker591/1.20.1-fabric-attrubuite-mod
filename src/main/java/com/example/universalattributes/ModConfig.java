package com.example.universalattributes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public final Map<String, AttributeRule> attributes = new LinkedHashMap<>();
    public final Map<String, EffectRule> effects = new LinkedHashMap<>();
    public final Map<String, DamageRule> damage = new LinkedHashMap<>();

    public static class AttributeRule {
        public boolean enabled = false;
        public double value = 0.0;
        public String displayName = "";
    }

    public static class EffectRule {
        public boolean enabled = false;
        public int amplifier = 0;
        public boolean hideParticles = false;
        public boolean showIcon = true;
        public String displayName = "";
    }

    public static class DamageRule {
        public double resistance = 0.0;
        public boolean immunity = false;
        public String displayName = "";
    }

    public static ModConfig load(Path path) {
        ModConfig loaded;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded == null) {
                    loaded = new ModConfig();
                }
            } catch (IOException | JsonSyntaxException exception) {
                UniversalAttributesMod.LOGGER.error("Failed to parse config, rebuilding defaults", exception);
                loaded = new ModConfig();
            }
        } else {
            loaded = new ModConfig();
        }

        loaded.mergeDiscoveredEntries();
        loaded.save(path);
        return loaded;
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            UniversalAttributesMod.LOGGER.error("Failed to save config", exception);
        }
    }

    private void mergeDiscoveredEntries() {
        var attributeIds = Registries.ATTRIBUTE.getIds().stream().sorted(Comparator.comparing(Identifier::toString)).toList();
        for (Identifier attributeId : attributeIds) {
            EntityAttribute attribute = Registries.ATTRIBUTE.get(attributeId);
            AttributeRule rule = attributes.computeIfAbsent(attributeId.toString(), id -> new AttributeRule());
            if (rule.displayName == null || rule.displayName.isBlank()) {
                rule.displayName = NameLocalizer.localizeAttribute(attributeId, attribute);
            }
            if (rule.value == 0.0) {
                rule.value = attribute.getDefaultValue();
            }
        }

        var effectIds = Registries.STATUS_EFFECT.getIds().stream().sorted(Comparator.comparing(Identifier::toString)).toList();
        for (Identifier effectId : effectIds) {
            StatusEffect effect = Registries.STATUS_EFFECT.get(effectId);
            EffectRule rule = effects.computeIfAbsent(effectId.toString(), id -> new EffectRule());
            if (rule.displayName == null || rule.displayName.isBlank()) {
                rule.displayName = NameLocalizer.localizeEffect(effectId, effect);
            }
        }

        addDamageType("minecraft:in_fire", "Fire");
        addDamageType("minecraft:on_fire", "Burning");
        addDamageType("minecraft:lava", "Lava");
        addDamageType("minecraft:hot_floor", "Hot Floor");
        addDamageType("minecraft:explosion", "Explosion");
        addDamageType("minecraft:player_explosion", "Player Explosion");
        addDamageType("minecraft:magic", "Magic");
        addDamageType("minecraft:indirect_magic", "Indirect Magic");
        addDamageType("minecraft:lightning_bolt", "Lightning");
        addDamageType("minecraft:freeze", "Freeze");
        addDamageType("minecraft:drown", "Drown");
        addDamageType("minecraft:starve", "Starve");
        addDamageType("minecraft:fall", "Fall");
        addDamageType("minecraft:fly_into_wall", "Fly Into Wall");
        addDamageType("minecraft:cactus", "Cactus");
        addDamageType("minecraft:sweet_berry_bush", "Sweet Berry Bush");
        addDamageType("minecraft:wither", "Wither");
        addDamageType("minecraft:dragon_breath", "Dragon Breath");
        addDamageType("minecraft:generic", "Generic");
    }

    private void addDamageType(String id, String displayName) {
        DamageRule rule = damage.computeIfAbsent(id, key -> new DamageRule());
        if (rule.displayName == null || rule.displayName.isBlank()) {
            rule.displayName = displayName;
        }
        rule.resistance = Math.max(0.0, Math.min(1.0, rule.resistance));
    }

    public static double getCurrentOrDefault(EntityAttributeInstance instance, EntityAttribute attribute) {
        return instance != null ? instance.getBaseValue() : attribute.getDefaultValue();
    }
}
