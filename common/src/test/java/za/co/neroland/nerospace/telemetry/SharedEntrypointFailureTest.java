package za.co.neroland.nerospace.telemetry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** MC-NEROSPACE-10: a pack-wide Fabric entrypoint failure is not reported as a Nerospace bug. */
class SharedEntrypointFailureTest {

    private static final String BUNDLE =
            "Could not execute entrypoint stage 'main' due to errors, provided by '%s' at 'x.Main'!";

    private static StackTraceElement frame(String cls, String method) {
        return new StackTraceElement(cls, method, cls + ".java", 1);
    }

    /** An UnsupportedOperationException from ImmutableMap.put, called through {@code caller}. */
    private static Throwable immutablePut(String caller) {
        Throwable t = new UnsupportedOperationException();
        t.setStackTrace(new StackTraceElement[] {
                frame("com.google.common.collect.ImmutableMap", "put"),
                frame("net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry", "register"),
                frame(caller, "onInitialize")});
        return t;
    }

    private static Throwable bundle(String provider, Throwable first, Throwable... later) {
        Throwable outer = new RuntimeException(String.format(BUNDLE, provider), first);
        for (Throwable t : later) {
            outer.addSuppressed(t);
        }
        return outer;
    }

    @Test
    void dropsWhenNerospaceFailedLikeEveryoneElse() {
        Throwable outer = bundle("aerialhell", immutablePut("fr.factionbedrock.aerialhell.AerialHell"),
                immutablePut("com.example.othermod.Init"),
                immutablePut("za.co.neroland.nerospace.fabric.NerospaceFabric"));
        assertTrue(TelemetryNoise.isSharedEntrypointFailure(outer));
    }

    @Test
    void keepsADifferentNerospaceFailure() {
        Throwable ours = new IllegalStateException("boom");
        ours.setStackTrace(new StackTraceElement[] {frame("za.co.neroland.nerospace.registry.ModBlocks", "init")});
        Throwable outer = bundle("aerialhell", immutablePut("fr.factionbedrock.aerialhell.AerialHell"), ours);
        assertFalse(TelemetryNoise.isSharedEntrypointFailure(outer));
    }

    @Test
    void keepsWhenNerospaceIsTheFirstFailure() {
        Throwable outer = bundle("nerospace", immutablePut("za.co.neroland.nerospace.fabric.NerospaceFabric"),
                immutablePut("com.example.othermod.Init"));
        assertFalse(TelemetryNoise.isSharedEntrypointFailure(outer));
    }

    @Test
    void keepsWhenANerolandModIsTheProvider() {
        Throwable outer = bundle("nerolandcore", immutablePut("za.co.neroland.nerolandcore.Init"),
                immutablePut("za.co.neroland.nerospace.fabric.NerospaceFabric"));
        assertFalse(TelemetryNoise.isSharedEntrypointFailure(outer));
    }

    @Test
    void ignoresOtherExceptions() {
        assertFalse(TelemetryNoise.isSharedEntrypointFailure(new RuntimeException("Something else")));
        assertFalse(TelemetryNoise.isSharedEntrypointFailure(null));
    }
}
