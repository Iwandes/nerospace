package za.co.neroland.nerospace.world;

import java.net.URI;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerospace.config.NerospaceConfig;

/**
 * Greets a player on join with a short welcome pointing at the Star Guide and a clickable link to the
 * mod's repository. Suppressed entirely when the {@code welcomeMessageEnabled} config key is false.
 * Cross-loader: the loader entry points invoke {@link #onPlayerJoin} from their join events
 * (NeoForge {@code PlayerEvent.PlayerLoggedInEvent} / Fabric
 * {@code ServerPlayConnectionEvents.JOIN}). The {@link ClickEvent.OpenUrl} record and {@code Style}
 * builder calls are vanilla on both 26.1.2 and 26.2, so no per-loader split is needed.
 *
 * <p>POPIA/GDPR: only sends an outbound chat message to the joining player; logs no player identity or
 * personal data.</p>
 */
public final class PlayerJoinHandler {

    private static final String REPO_URL = "https://github.com/Neroland/nerospace";

    private PlayerJoinHandler() {
    }

    /** Send the one-time welcome to a freshly joined server player. */
    public static void onPlayerJoin(ServerPlayer player) {
        // POPIA/GDPR: if an erasure request landed for this player while they were offline, the two
        // per-player attachments could not be reached then (both loaders expose attachments only through
        // a live Player). Finish the job now. No-op for everyone else.
        za.co.neroland.nerospace.data.NerospaceErasure.applyOnJoin(player);

        // Deliver any hook gate (Star Guide hint / oxygen top-up) that opened while this player was offline.
        za.co.neroland.nerospace.progression.QuestHooks.onJoin(player);

        // A crew member who disconnected mid-flight rejoins beside the rocket, not at their seat in the sky (2.1).
        za.co.neroland.nerospace.rocket.CrewLanding.onJoin(player);

        // Opt-out: config/nerospace.properties -> welcomeMessageEnabled=false joins silently. Checked
        // here (after the erasure sweep above, which must always run) and read fresh on every join, so
        // a `/neroland config reload` applies without a restart.
        if (!NerospaceConfig.welcomeMessageEnabled()) {
            return;
        }

        player.sendSystemMessage(Component.empty()
                .append(Component.literal("[Nerospace] ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.translatable("message.nerospace.welcome.intro").withStyle(ChatFormatting.GRAY)));

        Component link = Component.literal(REPO_URL).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(REPO_URL))));

        player.sendSystemMessage(Component.empty()
                .append(Component.translatable("message.nerospace.welcome.link").withStyle(ChatFormatting.GRAY))
                .append(link));
    }
}
