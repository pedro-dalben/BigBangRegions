package com.bigbangcraft.regions.mixin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerLevelMixinTest {
    @Test
    void delegatesCobblemonPokemonToItsSpawnEvent() throws Exception {
        Method classifier = ServerLevelMixin.class.getDeclaredMethod("isCobblemonPokemon", String.class);
        classifier.setAccessible(true);

        assertTrue((boolean) classifier.invoke(null, "com.cobblemon.mod.common.entity.pokemon.PokemonEntity"));
        assertFalse((boolean) classifier.invoke(null, "net.minecraft.world.entity.monster.Zombie"));
    }
}
