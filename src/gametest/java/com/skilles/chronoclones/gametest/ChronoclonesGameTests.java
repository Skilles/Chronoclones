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

/** Game test registration. Run with {@code ./gradlew runGameTestServer}. */
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

    /** Idempotent: both registration events arrive per launch. */
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
        BreakingGameTest.register();
        NudgeGameTest.register();
        CarrierGameTest.register();
        CloneInventoryGameTest.register();
        MenuPagingGameTest.register();
        AttackIntentGameTest.register();
        RoutineEditGameTest.register();
        ExperienceGameTest.register();
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
