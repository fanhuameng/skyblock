package com.jsorrell.skyblock.mixin;

import java.util.Properties;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;

import com.jsorrell.skyblock.gen.SkyBlockGenerationSettings;

// 世界生成器添加 新的生成选项
@Mixin(GeneratorOptions.class)
public class GeneratorOptionsMixin {

  @Inject(
      method = "fromProperties",
      at = @At("RETURN"),
      locals = LocalCapture.CAPTURE_FAILHARD,
      cancellable = true)
  private static void addSkyBlockGeneratorOptionWhenLoadingProperties(
      DynamicRegistryManager drm,
      Properties properties,
      CallbackInfoReturnable<GeneratorOptions> cir,
      String string,
      String string2,
      boolean generateStructures,
      String string4,
      String generatorSettingsName,
      long seed,
      Registry<DimensionType> dimensionTypeRegistry,
      Registry<Biome> biomeRegistry,
      Registry<ChunkGeneratorSettings> settingsRegistry) {

    // 判断 世界生成类型
    if (SkyBlockGenerationSettings.NAME.equals(generatorSettingsName)) {
      // 世界尺寸定义
      SimpleRegistry<DimensionOptions> dimensionOptions =
          SkyBlockGenerationSettings.getSkyBlockDimensionOptions(
              dimensionTypeRegistry, biomeRegistry, settingsRegistry, seed);

      GeneratorOptions generatorOptions = new GeneratorOptions(seed, generateStructures, false, dimensionOptions);

      cir.setReturnValue(generatorOptions);
    }
  }
}
