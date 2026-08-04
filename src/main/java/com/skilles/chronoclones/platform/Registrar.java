package com.skilles.chronoclones.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
//? if neoforge {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
//?}

/**
 * Loader-neutral registration. Registry classes declare entries through this and stay free of
 * loader imports; only this class knows how the active loader wants registrations delivered.
 *
 * <p>On NeoForge entries queue in a {@link DeferredRegister} until the mod entrypoint hands over
 * the mod bus; a Fabric build registers directly, since its registries accept writes during init.
 */
public final class Registrar<T> {

    private static final List<Registrar<?>> ALL = new ArrayList<>();

    //? if neoforge {
    private final DeferredRegister<T> deferred;

    private Registrar(Registry<T> registry, String namespace) {
        this.deferred = DeferredRegister.create(registry, namespace);
    }

    public <R extends T> Supplier<R> register(String name, Supplier<R> factory) {
        return deferred.register(name, factory);
    }

    /** NeoForge delivery: every registrar created so far joins the mod bus. */
    public static void registerAllTo(IEventBus modEventBus) {
        synchronized (ALL) {
            ALL.forEach(registrar -> registrar.deferred.register(modEventBus));
        }
    }
    //?}

    public static <T> Registrar<T> create(Registry<T> registry, String namespace) {
        Registrar<T> made = new Registrar<>(registry, namespace);
        synchronized (ALL) {
            ALL.add(made);
        }
        return made;
    }
}
