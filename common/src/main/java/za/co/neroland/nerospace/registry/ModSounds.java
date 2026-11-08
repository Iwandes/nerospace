package za.co.neroland.nerospace.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.registry.RegistrationProvider.RegistryEntry;

/**
 * Creature ambience sound events, ported cross-loader through {@link RegistrationProvider}. No
 * {@code .ogg} files ship: {@code assets/nerospace/sounds.json} aliases a fitting vanilla event for
 * each ({@code "type": "event"}); swapping in real audio later is a pure resource change.
 */
public final class ModSounds {

    public static final RegistrationProvider<SoundEvent> SOUND_EVENTS =
            RegistrationProvider.get(Registries.SOUND_EVENT, NerospaceCommon.MOD_ID);

    public static final RegistryEntry<SoundEvent> XERTZ_STALKER_AMBIENT = register("entity.xertz_stalker.ambient");
    public static final RegistryEntry<SoundEvent> XERTZ_STALKER_HURT = register("entity.xertz_stalker.hurt");
    public static final RegistryEntry<SoundEvent> XERTZ_STALKER_DEATH = register("entity.xertz_stalker.death");

    public static final RegistryEntry<SoundEvent> QUARTZ_CRAWLER_AMBIENT = register("entity.quartz_crawler.ambient");
    public static final RegistryEntry<SoundEvent> QUARTZ_CRAWLER_HURT = register("entity.quartz_crawler.hurt");
    public static final RegistryEntry<SoundEvent> QUARTZ_CRAWLER_DEATH = register("entity.quartz_crawler.death");

    public static final RegistryEntry<SoundEvent> GREENLING_AMBIENT = register("entity.greenling.ambient");
    public static final RegistryEntry<SoundEvent> GREENLING_HURT = register("entity.greenling.hurt");
    public static final RegistryEntry<SoundEvent> GREENLING_DEATH = register("entity.greenling.death");

    public static final RegistryEntry<SoundEvent> CINDER_STALKER_AMBIENT = register("entity.cinder_stalker.ambient");
    public static final RegistryEntry<SoundEvent> CINDER_STALKER_HURT = register("entity.cinder_stalker.hurt");
    public static final RegistryEntry<SoundEvent> CINDER_STALKER_DEATH = register("entity.cinder_stalker.death");

    public static final RegistryEntry<SoundEvent> FROST_STRIDER_AMBIENT = register("entity.frost_strider.ambient");
    public static final RegistryEntry<SoundEvent> FROST_STRIDER_HURT = register("entity.frost_strider.hurt");
    public static final RegistryEntry<SoundEvent> FROST_STRIDER_DEATH = register("entity.frost_strider.death");

    public static final RegistryEntry<SoundEvent> MEADOW_LOPER_AMBIENT = register("entity.meadow_loper.ambient");
    public static final RegistryEntry<SoundEvent> MEADOW_LOPER_HURT = register("entity.meadow_loper.hurt");
    public static final RegistryEntry<SoundEvent> MEADOW_LOPER_DEATH = register("entity.meadow_loper.death");

    public static final RegistryEntry<SoundEvent> EMBER_STRUTTER_AMBIENT = register("entity.ember_strutter.ambient");
    public static final RegistryEntry<SoundEvent> EMBER_STRUTTER_HURT = register("entity.ember_strutter.hurt");
    public static final RegistryEntry<SoundEvent> EMBER_STRUTTER_DEATH = register("entity.ember_strutter.death");

    public static final RegistryEntry<SoundEvent> WOOLLY_DRIFT_AMBIENT = register("entity.woolly_drift.ambient");
    public static final RegistryEntry<SoundEvent> WOOLLY_DRIFT_HURT = register("entity.woolly_drift.hurt");
    public static final RegistryEntry<SoundEvent> WOOLLY_DRIFT_DEATH = register("entity.woolly_drift.death");

    // Planet ambience (2.0.0): biome / dimension loops, one-shots and moods, plus the client weather loops. Every
    // event aliases vanilla sounds in sounds.json with its own volume and pitch.
    public static final RegistryEntry<SoundEvent> AMBIENT_LUNA_LOOP = register("ambient.luna.loop");
    public static final RegistryEntry<SoundEvent> AMBIENT_MARS_LOOP = register("ambient.mars.loop");
    public static final RegistryEntry<SoundEvent> AMBIENT_MARS_ADDITIONS = register("ambient.mars.additions");
    public static final RegistryEntry<SoundEvent> AMBIENT_MARS_STORM = register("ambient.mars.storm");
    public static final RegistryEntry<SoundEvent> AMBIENT_CINDARA_LOOP = register("ambient.cindara.loop");
    public static final RegistryEntry<SoundEvent> AMBIENT_CINDARA_ADDITIONS = register("ambient.cindara.additions");
    public static final RegistryEntry<SoundEvent> AMBIENT_CINDARA_MOOD = register("ambient.cindara.mood");
    public static final RegistryEntry<SoundEvent> AMBIENT_CINDARA_ASHFALL = register("ambient.cindara.ashfall");
    public static final RegistryEntry<SoundEvent> AMBIENT_GLACIRA_LOOP = register("ambient.glacira.loop");
    public static final RegistryEntry<SoundEvent> AMBIENT_GLACIRA_ADDITIONS = register("ambient.glacira.additions");
    public static final RegistryEntry<SoundEvent> AMBIENT_GLACIRA_DIAMOND_DUST = register("ambient.glacira.diamond_dust");
    public static final RegistryEntry<SoundEvent> AMBIENT_GREENXERTZ_LOOP = register("ambient.greenxertz.loop");
    public static final RegistryEntry<SoundEvent> AMBIENT_GREENXERTZ_ADDITIONS = register("ambient.greenxertz.additions");
    public static final RegistryEntry<SoundEvent> AMBIENT_GREENXERTZ_MOOD = register("ambient.greenxertz.mood");
    public static final RegistryEntry<SoundEvent> AMBIENT_GREENXERTZ_BLOOM = register("ambient.greenxertz.bloom");
    public static final RegistryEntry<SoundEvent> AMBIENT_NYXARA_LOOP = register("ambient.nyxara.loop");
    public static final RegistryEntry<SoundEvent> AMBIENT_NYXARA_ADDITIONS = register("ambient.nyxara.additions");
    public static final RegistryEntry<SoundEvent> AMBIENT_NYXARA_MOOD = register("ambient.nyxara.mood");
    public static final RegistryEntry<SoundEvent> AMBIENT_NYXARA_FLARE = register("ambient.nyxara.flare");
    public static final RegistryEntry<SoundEvent> AMBIENT_NYXARA_FLARE_ONSET = register("ambient.nyxara.flare_onset");
    public static final RegistryEntry<SoundEvent> AMBIENT_STATION_LOOP = register("ambient.station.loop");
    public static final RegistryEntry<SoundEvent> AMBIENT_STATION_ADDITIONS = register("ambient.station.additions");

    // Mars rover (2.1, D16): motor hum while driving, idle tick, horn on the jump key at a standstill, deploy/pack-up.
    public static final RegistryEntry<SoundEvent> ROVER_MOTOR = register("entity.rover.motor");
    public static final RegistryEntry<SoundEvent> ROVER_IDLE = register("entity.rover.idle");
    public static final RegistryEntry<SoundEvent> ROVER_HORN = register("entity.rover.horn");
    public static final RegistryEntry<SoundEvent> ROVER_PLACE = register("entity.rover.place");
    public static final RegistryEntry<SoundEvent> ROVER_PICKUP = register("entity.rover.pickup");

    private static RegistryEntry<SoundEvent> register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, path);
        return SOUND_EVENTS.register(path, key -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {
    }

    public static void init() {
    }
}
