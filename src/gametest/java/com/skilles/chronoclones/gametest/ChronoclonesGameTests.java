package com.skilles.chronoclones.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.skilles.chronoclones.Chronoclones;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Game test registration for 26.x.
 *
 * <p>26.x removed the {@code @GameTest} annotation entirely; tests are now data-driven through two
 * registries. A test is a {@code Consumer<GameTestHelper>} in {@code Registries.TEST_FUNCTION},
 * plus a {@code GameTestInstance} in {@code Registries.TEST_INSTANCE} that points at it and carries
 * the run settings. Both halves are required — a function with no instance never runs, and an
 * instance with no function fails to resolve with "missing test function".
 *
 * <p><b>Test functions cannot go through {@code TestFunctionLoader}.</b> That is the mechanism
 * vanilla uses, but {@code TEST_FUNCTION} is bootstrapped during {@code BuiltInRegistries} class
 * initialisation, which happens long before any mod is constructed — a loader registered from a mod
 * entrypoint is always too late. Instead the functions are registered with a {@link DeferredRegister}
 * over the same registry, which NeoForge fills during {@code RegisterEvent} while built-in
 * registries are still unfrozen. Test instances are then registered later, on
 * {@link RegisterGameTestsEvent}, by which point the functions resolve.
 *
 * <p>These live in {@code src/gametest} rather than in {@code src/main}: they need a running server,
 * so plain JUnit cannot host them, but registering test functions from a released mod would leave
 * every install carrying a couple of dozen test ids visible in {@code /test} and in registry dumps.
 * Their own source set is on the classpath for the dev runs and absent from the jar. Off-runtime
 * logic is covered by plain JUnit instead — see the {@code src/test} suite.
 *
 * <p>That split is also why nothing in {@code src/main} may name this class. Registration therefore
 * hangs off {@link RegisterEvent} rather than a {@code DeferredRegister} handed to the mod
 * constructor — same window while built-in registries are unfrozen, no call from the entrypoint.
 *
 * <p>Run with {@code ./gradlew runGameTestServer}, or {@code /test runall} in a dev client.
 */
@EventBusSubscriber(modid = Chronoclones.MODID)
public final class ChronoclonesGameTests {

    /** An empty structure: these tests build their own scenery rather than loading a template. */
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    private static final int DEFAULT_MAX_TICKS = 200;
    private static final int DEFAULT_SETUP_TICKS = 0;

    /** Declared tests, paired with the instance settings they should run under. */
    private record Entry(String name, int maxTicks, Consumer<GameTestHelper> function) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private ChronoclonesGameTests() {}

    /**
     * Fills {@link #ENTRIES}. Idempotent, because both registration events can arrive per launch and
     * declaring every test twice would register duplicate ids.
     */
    private static synchronized void declare() {
        if (!ENTRIES.isEmpty()) {
            return;
        }
        AttributionGameTest.register();
        ReplayGameTest.register();
        ShardGameTest.register();
        CaptureGameTest.register();
        AnchorDropsGameTest.register();
        InteractionGameTest.register();
        CoherenceGameTest.register();
        NudgeGameTest.register();
    }

    /** Declares a test. The name becomes both the function id and the instance id. */
    static void add(String name, Consumer<GameTestHelper> function) {
        add(name, DEFAULT_MAX_TICKS, function);
    }

    static void add(String name, int maxTicks, Consumer<GameTestHelper> function) {
        ENTRIES.add(new Entry(name, maxTicks, function));
    }

    /**
     * The functions half, during the window while {@code TEST_FUNCTION} is still unfrozen.
     *
     * <p>{@code TEST_FUNCTION} is bootstrapped during {@code BuiltInRegistries} class initialisation,
     * long before any mod is constructed, so vanilla's {@code TestFunctionLoader} is always too late.
     * This is the same window a {@code DeferredRegister} would use, reached without main having to
     * hand one to the mod constructor.
     */
    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
            return;
        }
        declare();
        event.register(Registries.TEST_FUNCTION, registry -> {
            for (Entry entry : ENTRIES) {
                registry.register(Chronoclones.id(entry.name()), entry.function());
            }
        });
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        declare();
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Chronoclones.id("default"));

        for (Entry entry : ENTRIES) {
            Identifier id = Chronoclones.id(entry.name());
            event.registerTest(id, new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, EMPTY_STRUCTURE,
                            entry.maxTicks(), DEFAULT_SETUP_TICKS, true)));
        }
    }
}
