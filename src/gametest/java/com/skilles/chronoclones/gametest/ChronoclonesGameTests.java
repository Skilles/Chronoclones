package com.skilles.chronoclones.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.skilles.chronoclones.Chronoclones;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
//?}

/**
 * The mod's test functions. Each name here pairs with a generated
 * {@code data/chronoclones/test_instance/<name>.json}, which carries the per-test settings
 * (environment, structure, max ticks) on both loaders; adding a test means adding its JSON.
 */
//? if neoforge {
//? if >=26 {
@EventBusSubscriber(modid = Chronoclones.MODID)
//?}
//?}
public final class ChronoclonesGameTests {

    private record Entry(String name, Consumer<GameTestHelper> function) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private ChronoclonesGameTests() {}

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
        MenuStepGameTest.register();
        RunStateGameTest.register();
        FakePlayerGameTest.register();
        HeldUseGameTest.register();
        ItemMatchGameTest.register();
        PlacementGameTest.register();
        AnchorSessionGameTest.register();
        ReportGameTest.register();
        RedstoneGameTest.register();
        AnchorInsertGameTest.register();
    }

    static void add(String name, Consumer<GameTestHelper> function) {
        ENTRIES.add(new Entry(name, function));
    }

    /** Runs one declared test by name; the pre-26 annotated shims dispatch through here. */
    public static void run(String name, GameTestHelper helper) {
        declare();
        for (Entry entry : ENTRIES) {
            if (entry.name().equals(name)) {
                try {
                    entry.function().accept(helper);
                } catch (RuntimeException | Error thrown) {
                    Chronoclones.LOGGER.error("gametest {} threw", name, thrown);
                    throw thrown;
                }
                return;
            }
        }
        helper.fail("unknown test function " + name);
    }

    /** @param maxTicks documentation only; the runtime value lives in the test's JSON */
    static void add(String name, int maxTicks, Consumer<GameTestHelper> function) {
        add(name, function);
    }

    //? if neoforge {
    //? if >=26 {
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
    //?}
    // Pre-26 has no test-function registry; the @GameTestHolder-annotated shims carry the tests.
    //?} else {
    /*// Called from the gametest dev-mod entrypoint; Fabric registries accept writes during init.
    public static void registerFunctions() {
        declare();
        for (Entry entry : ENTRIES) {
            net.minecraft.core.Registry.register(
                    net.minecraft.core.registries.BuiltInRegistries.TEST_FUNCTION,
                    Chronoclones.id(entry.name()), entry.function());
        }
    }
    *///?}
}
