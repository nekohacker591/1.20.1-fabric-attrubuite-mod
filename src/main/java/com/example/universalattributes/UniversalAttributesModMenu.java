package com.example.universalattributes;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UniversalAttributesModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return this::createConfigScreen;
    }

    private Screen createConfigScreen(Screen parent) {
        ModConfig config = UniversalAttributesMod.getConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("text.universalattributes.title"))
                .setSavingRunnable(() -> {
                    config.save(UniversalAttributesMod.CONFIG_PATH);
                    UniversalAttributesMod.reloadConfig();
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null && client.getServer() != null) {
                        UniversalAttributesMod.refreshAndApplyToOnlinePlayers(client.getServer());
                    }
                });

        ConfigEntryBuilder eb = builder.entryBuilder();

        addAttributeCategory(builder, eb, Text.translatable("text.universalattributes.section.attributes"), config.attributes, AttributeBucket.PLAYER);
        addAttributeCategory(builder, eb, Text.translatable("text.universalattributes.section.mob_attributes"), config.attributes, AttributeBucket.MOB);
        addAttributeCategory(builder, eb, Text.translatable("text.universalattributes.section.world_attributes"), config.attributes, AttributeBucket.WORLD);
        addAttributeCategory(builder, eb, Text.translatable("text.universalattributes.section.game_attributes"), config.attributes, AttributeBucket.GAME);
        addEffectsCategory(builder, eb, config.effects);
        addDamageCategory(builder, eb, config.damage);
        addNamespaceCategories(builder, eb, config);

        builder.setGlobalized(true);
        builder.setTransparentBackground(true);
        builder.setDoesConfirmSave(false);

        return builder.build();
    }

    private void addAttributeCategory(ConfigBuilder builder, ConfigEntryBuilder eb, Text title, Map<String, ModConfig.AttributeRule> attributes, AttributeBucket bucket) {
        ConfigCategory category = builder.getOrCreateCategory(title);
        attributes.entrySet().stream()
                .filter(e -> bucket.matches(e.getKey()))
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    ModConfig.AttributeRule rule = entry.getValue();
                    Text shortLabel = shortLabel(rule.displayName, entry.getKey());
                    Text tooltip = Text.literal(entry.getKey());
                    category.addEntry(eb.startBooleanToggle(shortLabel.copy().append(" • Enabled"), rule.enabled)
                            .setDefaultValue(false)
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> rule.enabled = v)
                            .build());
                    category.addEntry(eb.startDoubleField(shortLabel.copy().append(" • Value"), rule.value)
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> {
                                rule.value = v;
                                rule.enabled = true;
                            })
                            .build());
                });
    }

    private void addEffectsCategory(ConfigBuilder builder, ConfigEntryBuilder eb, Map<String, ModConfig.EffectRule> effects) {
        ConfigCategory category = builder.getOrCreateCategory(Text.translatable("text.universalattributes.section.effects"));
        effects.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    ModConfig.EffectRule rule = entry.getValue();
                    Text shortLabel = shortLabel(rule.displayName, entry.getKey());
                    Text tooltip = Text.literal(entry.getKey());
                    category.addEntry(eb.startBooleanToggle(shortLabel.copy().append(" • Enabled"), rule.enabled)
                            .setDefaultValue(false)
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> rule.enabled = v)
                            .build());
                    category.addEntry(eb.startIntField(shortLabel.copy().append(" • Level"), rule.amplifier)
                            .setMin(0)
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> {
                                rule.amplifier = v;
                                rule.enabled = true;
                            })
                            .build());
                    category.addEntry(eb.startBooleanToggle(shortLabel.copy().append(" • Hide Particles"), rule.hideParticles)
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> rule.hideParticles = v)
                            .build());
                    category.addEntry(eb.startBooleanToggle(shortLabel.copy().append(" • Show Icon"), rule.showIcon)
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> rule.showIcon = v)
                            .build());
                });
    }

    private void addDamageCategory(ConfigBuilder builder, ConfigEntryBuilder eb, Map<String, ModConfig.DamageRule> damageRules) {
        ConfigCategory category = builder.getOrCreateCategory(Text.translatable("text.universalattributes.section.damage"));
        damageRules.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    ModConfig.DamageRule rule = entry.getValue();
                    Text shortLabel = shortLabel(rule.displayName, entry.getKey());
                    Text tooltip = Text.literal(entry.getKey());
                    category.addEntry(eb.startDoubleField(shortLabel.copy().append(" • Resistance"), rule.resistance)
                            .setMin(0.0)
                            .setMax(1.0)
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> rule.resistance = v)
                            .build());
                    category.addEntry(eb.startBooleanToggle(shortLabel.copy().append(" • Immunity"), rule.immunity)
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> rule.immunity = v)
                            .build());
                });
    }

    private void addNamespaceCategories(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig config) {
        Map<String, List<Map.Entry<String, ModConfig.AttributeRule>>> byNamespace = new LinkedHashMap<>();
        config.attributes.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    String namespace = namespaceOf(entry.getKey());
                    byNamespace.computeIfAbsent(namespace, key -> new ArrayList<>()).add(entry);
                });

        byNamespace.forEach((namespace, entries) -> {
            ConfigCategory category = builder.getOrCreateCategory(Text.literal("Mod Filter: " + namespace));
            for (Map.Entry<String, ModConfig.AttributeRule> entry : entries) {
                ModConfig.AttributeRule rule = entry.getValue();
                Text shortLabel = shortLabel(rule.displayName, entry.getKey());
                Text tooltip = Text.literal(entry.getKey());
                category.addEntry(eb.startBooleanToggle(shortLabel.copy().append(" • Enabled"), rule.enabled)
                        .setTooltip(tooltip)
                        .setSaveConsumer(v -> rule.enabled = v)
                        .build());
                category.addEntry(eb.startDoubleField(shortLabel.copy().append(" • Value"), rule.value)
                        .setTooltip(tooltip)
                        .setSaveConsumer(v -> {
                            rule.value = v;
                            rule.enabled = true;
                        })
                        .build());
            }
        });
    }

    private String namespaceOf(String id) {
        int split = id.indexOf(':');
        return split > 0 ? id.substring(0, split) : "unknown";
    }

    private Text shortLabel(String displayName, String id) {
        String label = (displayName == null || displayName.isBlank()) ? id : displayName;
        if (label.length() > 28) {
            label = label.substring(0, 25) + "...";
        }
        return Text.literal(label);
    }

    private enum AttributeBucket {
        PLAYER,
        MOB,
        WORLD,
        GAME;

        private static final String[] PLAYER_KEYS = {"player", "health", "hunger", "luck"};
        private static final String[] MOB_KEYS = {"zombie", "skeleton", "creeper", "spider", "enderman", "wither", "dragon", "horse", "bee", "piglin", "villager", "monster"};
        private static final String[] WORLD_KEYS = {"gravity", "fall", "water", "lava", "fire", "freeze", "temperature", "oxygen"};

        boolean matches(String id) {
            String lower = id.toLowerCase();
            return switch (this) {
                case PLAYER -> containsAny(lower, PLAYER_KEYS) || lower.startsWith("minecraft:generic.max_health");
                case MOB -> containsAny(lower, MOB_KEYS);
                case WORLD -> containsAny(lower, WORLD_KEYS);
                case GAME -> !PLAYER.matches(id) && !MOB.matches(id) && !WORLD.matches(id);
            };
        }

        private static boolean containsAny(String value, String[] keys) {
            for (String key : keys) {
                if (value.contains(key)) {
                    return true;
                }
            }
            return false;
        }
    }
}
