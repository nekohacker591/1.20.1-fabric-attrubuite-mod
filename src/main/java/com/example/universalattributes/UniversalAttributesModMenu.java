package com.example.universalattributes;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

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
                .setSavingRunnable(() -> config.save(UniversalAttributesMod.CONFIG_PATH));

        ConfigEntryBuilder eb = builder.entryBuilder();

        ConfigCategory attributes = builder.getOrCreateCategory(Text.translatable("text.universalattributes.section.attributes"));
        for (Map.Entry<String, ModConfig.AttributeRule> entry : config.attributes.entrySet()) {
            ModConfig.AttributeRule rule = entry.getValue();
            Text label = Text.literal(rule.displayName + " [" + entry.getKey() + "]");
            attributes.addEntry(eb.startBooleanToggle(label.copy().append(" - Enabled"), rule.enabled)
                    .setDefaultValue(false)
                    .setSaveConsumer(v -> rule.enabled = v)
                    .build());
            attributes.addEntry(eb.startDoubleField(label.copy().append(" - Value"), rule.value)
                    .setSaveConsumer(v -> rule.value = v)
                    .build());
        }

        ConfigCategory effects = builder.getOrCreateCategory(Text.translatable("text.universalattributes.section.effects"));
        for (Map.Entry<String, ModConfig.EffectRule> entry : config.effects.entrySet()) {
            ModConfig.EffectRule rule = entry.getValue();
            Text label = Text.literal(rule.displayName + " [" + entry.getKey() + "]");
            effects.addEntry(eb.startBooleanToggle(label.copy().append(" - Enabled"), rule.enabled)
                    .setDefaultValue(false)
                    .setSaveConsumer(v -> rule.enabled = v)
                    .build());
            effects.addEntry(eb.startIntField(label.copy().append(" - Level"), rule.amplifier)
                    .setMin(0)
                    .setSaveConsumer(v -> rule.amplifier = v)
                    .build());
            effects.addEntry(eb.startBooleanToggle(label.copy().append(" - Hide Particles"), rule.hideParticles)
                    .setSaveConsumer(v -> rule.hideParticles = v)
                    .build());
            effects.addEntry(eb.startBooleanToggle(label.copy().append(" - Show Icon"), rule.showIcon)
                    .setSaveConsumer(v -> rule.showIcon = v)
                    .build());
        }

        ConfigCategory damage = builder.getOrCreateCategory(Text.translatable("text.universalattributes.section.damage"));
        for (Map.Entry<String, ModConfig.DamageRule> entry : config.damage.entrySet()) {
            ModConfig.DamageRule rule = entry.getValue();
            Text label = Text.literal(rule.displayName + " [" + entry.getKey() + "]");
            damage.addEntry(eb.startDoubleField(label.copy().append(" - Resistance"), rule.resistance)
                    .setMin(0.0)
                    .setMax(1.0)
                    .setSaveConsumer(v -> rule.resistance = v)
                    .build());
            damage.addEntry(eb.startBooleanToggle(label.copy().append(" - Immunity"), rule.immunity)
                    .setSaveConsumer(v -> rule.immunity = v)
                    .build());
        }

        builder.setGlobalized(true);
        builder.setTransparentBackground(true);
        builder.setDoesConfirmSave(false);
        builder.setAfterInitConsumer(screen -> {
            // Cloth Config includes a built-in search bar that can filter massive config lists.
        });

        return builder.build();
    }
}
