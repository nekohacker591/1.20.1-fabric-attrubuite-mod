package com.example.universalattributes;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class NameLocalizer {
    private NameLocalizer() {
    }

    public static String localizeAttribute(Identifier id, EntityAttribute attribute) {
        String fallback = prettify(id);
        try {
            String translated = Text.translatable(attribute.getTranslationKey()).getString();
            if (!translated.equals(attribute.getTranslationKey())) {
                return translated;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public static String localizeEffect(Identifier id, StatusEffect effect) {
        String fallback = prettify(id);
        try {
            String translated = Text.translatable(effect.getTranslationKey()).getString();
            if (!translated.equals(effect.getTranslationKey())) {
                return translated;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static String prettify(Identifier id) {
        String path = id.getPath().replace('_', ' ').replace('.', ' ');
        if (path.isBlank()) {
            return id.toString();
        }
        return Character.toUpperCase(path.charAt(0)) + path.substring(1) + " (" + id + ")";
    }
}
