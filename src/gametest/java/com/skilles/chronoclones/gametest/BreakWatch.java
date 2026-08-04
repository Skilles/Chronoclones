package com.skilles.chronoclones.gametest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;

/**
 * A test's window onto block-break attempts at one position: observe who broke, or veto the
 * break like a protection mod would. Close it when the test is done.
 */
final class BreakWatch implements AutoCloseable {

    /** One observed break attempt. */
    interface Attempt {
        UUID playerId();

        void cancel();
    }

    private final BlockPos absolutePos;
    private final Consumer<Attempt> handler;

    private BreakWatch(BlockPos absolutePos, Consumer<Attempt> handler) {
        this.absolutePos = absolutePos;
        this.handler = handler;
    }

    private static final List<BreakWatch> ACTIVE = new CopyOnWriteArrayList<>();

    static BreakWatch at(BlockPos absolutePos, Consumer<Attempt> handler) {
        BreakWatch watch = new BreakWatch(absolutePos, handler);
        ACTIVE.add(watch);
        Hook.install();
        return watch;
    }

    @Override
    public void close() {
        ACTIVE.remove(this);
    }

    private static void dispatch(BlockPos pos, Attempt attempt) {
        for (BreakWatch watch : ACTIVE) {
            if (watch.absolutePos.equals(pos)) {
                watch.handler.accept(attempt);
            }
        }
    }

    /**
     * The single loader hook every watch shares: loader events cannot unregister, so it stays
     * installed and consults the active list.
     */
    private static final class Hook {

        private static boolean installed;

        private static synchronized void install() {
            if (installed) {
                return;
            }
            installed = true;
            //? if neoforge {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    net.neoforged.bus.api.EventPriority.HIGHEST, false,
                    net.neoforged.neoforge.event.level.block.BreakBlockEvent.class,
                    event -> dispatch(event.getPos(), new Attempt() {
                        @Override
                        public UUID playerId() {
                            return event.getPlayer().getUUID();
                        }

                        @Override
                        public void cancel() {
                            event.setCanceled(true);
                        }
                    }));
            //?} else {
            /*net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
                    (level, player, pos, state, blockEntity) -> {
                        boolean[] cancelled = {false};
                        dispatch(pos, new Attempt() {
                            @Override
                            public UUID playerId() {
                                return player.getUUID();
                            }

                            @Override
                            public void cancel() {
                                cancelled[0] = true;
                            }
                        });
                        return !cancelled[0];
                    });
            *///?}
        }

        private Hook() {}
    }
}
