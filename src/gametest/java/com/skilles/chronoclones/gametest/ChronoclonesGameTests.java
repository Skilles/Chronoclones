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
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = Chronoclones.MODID)
public final class ChronoclonesGameTests {

    private static final Identifier PLOT_STRUCTURE = Chronoclones.id("test_plot");

    private static final int DEFAULT_MAX_TICKS = 200;
    private static final int DEFAULT_SETUP_TICKS = 0;

    private static final int PLOT_PADDING = 2;

    private record Entry(String name, int maxTicks, Consumer<GameTestHelper> function) {}

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
        add(name, DEFAULT_MAX_TICKS, function);
    }

    static void add(String name, int maxTicks, Consumer<GameTestHelper> function) {
        ENTRIES.add(new Entry(name, maxTicks, function));
    }

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
                    new TestData<>(environment, PLOT_STRUCTURE,
                            entry.maxTicks(), DEFAULT_SETUP_TICKS, true,
                            Rotation.NONE, false, 1, 1, false, PLOT_PADDING)));
        }
    }
}
