package net.caduzz.tablecraft;

import java.util.function.UnaryOperator;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/**
 * Parâmetros para extensão de enums (ex.: Rarity vermelho).
 * Classe separada para evitar carregamento antecipado do mod.
 */
public class ModEnumParams {

    /** Raridade customizada vermelha para o nome do item (em vez de amarelo/UNCOMMON). */
    public static final EnumProxy<Rarity> RED_RARITY_PROXY = new EnumProxy<>(
            Rarity.class,
            -1,
            TableCraft.MOD_ID + ":red",
            (UnaryOperator<Style>) style -> style.withColor(ChatFormatting.RED));
}
