package za.co.neroland.nerospace.telemetry;

import java.util.List;
import java.util.Locale;

import io.sentry.SentryEvent;

/**
 * Known-noise rules for {@link NerospaceTelemetry}'s {@code beforeSend} gate: events that name Nerospace somewhere in
 * the stack but that Nerospace did not cause and cannot fix. Each rule is narrow and documented so a real Nerospace
 * bug is never silenced by accident.
 *
 * <ul>
 *   <li><b>Another mod's own exception.</b> The deepest cause was thrown inside a third-party mod's code
 *       (its innermost frame is outside the JDK, Minecraft, the mod loaders, the libraries they ship and the
 *       Neroland mods). A Nerospace frame on that stack only means another mod called into us — e.g. a recipe
 *       viewer constructing one of our entities while its own config was not loaded yet.</li>
 *   <li><b>World-data mismatch.</b> Vanilla's {@code BlockEntity.validateBlockState} rejecting saved block
 *       entity data whose block was replaced (another mod remapped or removed blocks in that chunk). Vanilla
 *       logs it and discards the stale block entity; the world keeps working, and nothing in Nerospace is wrong.</li>
 *   <li><b>Follow-on registry lookup during registration</b> (MC-NEROSPACE-R). A Nerospace entry (block item,
 *       block-entity type) looking up a Nerospace block that was never registered, inside the loader's register
 *       event. The block register event aborted before reaching Nerospace (no Nerospace constructor failure was
 *       reported alongside, so another mod's registration failure is the likely cause), leaving every Nerospace
 *       block unbound for the next registry to trip over. If one of our own constructors failed instead, the
 *       registration factories already report that real exception at its source; this symptom names the victim,
 *       never the cause, either way. Matched
 *       only on the loaders' exact unbound-holder messages with a registration frame on the stack, so an unbound
 *       lookup at runtime still reports.</li>
 *   <li><b>Shared Fabric entrypoint failure</b> (MC-NEROSPACE-10). Fabric gathers every failing {@code main}
 *       entrypoint into one exception named after the first failing mod. When that mod is not a Neroland one and
 *       every Nerospace failure in the bundle ends at the same exception and the same innermost frame as that
 *       first failure, the pack itself is broken (for example another mod froze the default-attribute map, so
 *       every mod registering entity attributes failed alike) and Nerospace is one victim among many. A
 *       Nerospace failure that differs from the first one still reports.</li>
 * </ul>
 */
final class TelemetryNoise {

    /** Frames from these packages count as "the platform", never as another mod's code. */
    private static final List<String> PLATFORM_PREFIXES = List.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "net.minecraft.", "com.mojang.",
            "net.neoforged.", "net.minecraftforge.", "cpw.mods.", "net.fabricmc.",
            "org.spongepowered.", "com.llamalad7.",
            "com.google.", "it.unimi.", "io.netty.", "org.apache.", "org.lwjgl.", "org.slf4j.",
            "io.sentry.",
            "za.co.neroland.");

    private TelemetryNoise() {
    }

    /** True when {@code event} matches a known-noise rule and should be dropped. */
    static boolean isNoise(SentryEvent event) {
        Throwable root = rootCause(event.getThrowable());
        if (root == null) {
            return false;
        }
        return isStaleBlockEntity(root) || isUnboundDuringRegistration(root) || thrownByAnotherMod(root)
                || isSharedEntrypointFailure(event.getThrowable());
    }

    /** Fabric's wording for the bundled exception; group 1 is the first failing mod's id. */
    private static final java.util.regex.Pattern FABRIC_ENTRYPOINT = java.util.regex.Pattern.compile(
            "^Could not execute entrypoint stage '[^']*' due to errors, provided by '([^']+)'");
    private static final String OWN_PACKAGE = "za.co.neroland.nerospace.";

    static boolean isSharedEntrypointFailure(Throwable outer) {
        if (outer == null || outer.getMessage() == null) {
            return false;
        }
        java.util.regex.Matcher m = FABRIC_ENTRYPOINT.matcher(outer.getMessage());
        if (!m.find() || m.group(1).toLowerCase(Locale.ROOT).startsWith("nero")) {
            return false;
        }
        Throwable first = outer.getCause();
        Throwable firstRoot = rootCause(first);
        if (first == null || firstRoot == null || touchesNerospace(first)) {
            return false;
        }
        // Fabric adds later failures as suppressed on the bundle; tolerate them on the first cause too.
        List<Throwable> others = new java.util.ArrayList<>(List.of(outer.getSuppressed()));
        others.addAll(List.of(first.getSuppressed()));
        boolean anyOurs = false;
        for (Throwable other : others) {
            if (!touchesNerospace(other)) {
                continue;
            }
            anyOurs = true;
            if (!sameFailure(rootCause(other), firstRoot)) {
                return false; // Nerospace failed differently: a real report
            }
        }
        return anyOurs;
    }

    private static boolean sameFailure(Throwable a, Throwable b) {
        if (a == null || b == null || a.getClass() != b.getClass()) {
            return false;
        }
        StackTraceElement[] fa = a.getStackTrace();
        StackTraceElement[] fb = b.getStackTrace();
        if (fa.length == 0 || fb.length == 0) {
            return false;
        }
        return fa[0].getClassName().equals(fb[0].getClassName())
                && fa[0].getMethodName().equals(fb[0].getMethodName());
    }

    private static boolean touchesNerospace(Throwable t) {
        Throwable current = t;
        for (int depth = 0; current != null && depth < 32; depth++) {
            for (StackTraceElement frame : current.getStackTrace()) {
                if (frame.getClassName().startsWith(OWN_PACKAGE)) {
                    return true;
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isUnboundDuringRegistration(Throwable root) {
        String message = root.getMessage();
        if (!(root instanceof NullPointerException) || message == null) {
            return false;
        }
        // NeoForge DeferredHolder / Forge RegistryObject wording for a holder whose value was never bound.
        if (!message.startsWith("Trying to access unbound value") && !message.startsWith("Registry Object not present")) {
            return false;
        }
        for (StackTraceElement frame : root.getStackTrace()) {
            String cls = frame.getClassName();
            if (cls.endsWith(".RegisterEvent") || cls.endsWith(".DeferredRegister")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStaleBlockEntity(Throwable root) {
        String message = root.getMessage();
        if (!(root instanceof IllegalStateException) || message == null) {
            return false;
        }
        if (!message.startsWith("Invalid block entity ") || !message.contains(" state at ")) {
            return false;
        }
        for (StackTraceElement frame : root.getStackTrace()) {
            if ("validateBlockState".equals(frame.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean thrownByAnotherMod(Throwable root) {
        StackTraceElement[] frames = root.getStackTrace();
        if (frames.length == 0) {
            return false;
        }
        String thrower = frames[0].getClassName();
        if (thrower == null || thrower.isEmpty()) {
            return false;
        }
        String lower = thrower.toLowerCase(Locale.ROOT);
        for (String prefix : PLATFORM_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return false;
            }
        }
        // Mixin-generated or hidden classes carry no stable package; don't guess about them.
        return thrower.indexOf('.') > 0 && !thrower.startsWith("$");
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        for (int depth = 0; current != null && current.getCause() != null && current.getCause() != current
                && depth < 32; depth++) {
            current = current.getCause();
        }
        return current;
    }
}
