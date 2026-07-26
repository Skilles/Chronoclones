package com.skilles.chronoclones.replay;

/**
 * How specific a routine is about the item transfers it performs.
 *
 * <p>Three independent questions, because a recorded transfer states three separate facts and a
 * routine rarely means all three of them. "Put eight coal in the left slot" might mean <em>that
 * square</em> (a sorting system), or <em>coal specifically</em> (a furnace worth fuelling properly),
 * or <em>eight of them</em> (a recipe), or any combination — and one setting covering all three
 * forces the strictest reading on someone who only wanted one of them.
 *
 * <p>Read these as <b>specificity, not correctness</b>. A loose axis is not a fallback for when
 * things go wrong; it is a statement that the recorded value was incidental. Nothing here decides
 * whether an action succeeds, with the one exception noted below.
 *
 * <h2>The axes</h2>
 *
 * <p><b>Slot.</b> On, a click lands on the square the recording named or nowhere. Off,
 * {@link SlotChoice} may redirect it to another square of the same kind when the named one is full.
 *
 * <p><b>Item.</b> On, only the recorded stack will do — same item, same components, so an enchanted
 * pickaxe is not satisfied by a plain one. Off, anything the anchor is stocked with will do, with
 * the recorded item preferred when it is available. See {@link ContainerCarrier} for the ladder.
 *
 * <p><b>Quantity.</b> On, staging takes up to the recorded count. Off, the count was incidental and
 * staging takes as much as the anchor has, up to what one slot holds — so a routine taught with a
 * handful runs with a stack.
 *
 * <h2>What can still fail</h2>
 *
 * <p>Only one thing: a layout entry that finds nothing at all to stage. That is the difference
 * between a routine running at reduced volume and a routine clicking at empty squares, and it stays
 * a reported failure at every setting. Everything else these flags control is a preference.
 *
 * <p><b>Not the block axis.</b> Whether a break accepts a block other than the recorded one is
 * {@link Coherence}, bought with an Chrono Lens, and it stays separate on purpose: lenient block
 * matching makes an anchor able to do <em>more</em>, which is worth charging for, whereas everything
 * here only ever narrows what a routine will touch.
 */
public record TransferPrecision(boolean slot, boolean item, boolean quantity) {

    /** What a fresh anchor uses: the recorded values are treated as incidental throughout. */
    public static final TransferPrecision NONE = new TransferPrecision(false, false, false);

    private static final int SLOT_BIT = 1;
    private static final int ITEM_BIT = 2;
    private static final int QUANTITY_BIT = 4;

    /**
     * The three flags as one int, for the container data the menu syncs and the payload the drawer
     * sends.
     *
     * <p>All eight combinations are meaningful and none is normalised away. Item and quantity say
     * something about <em>what</em> gets staged, which is a fact about the anchor's own inventory
     * and holds whether or not the destination square is pinned.
     */
    public int pack() {
        return (slot ? SLOT_BIT : 0) | (item ? ITEM_BIT : 0) | (quantity ? QUANTITY_BIT : 0);
    }

    /** Unknown bits are ignored rather than rejected — this decodes a client's packet. */
    public static TransferPrecision unpack(int packed) {
        return new TransferPrecision(
                (packed & SLOT_BIT) != 0,
                (packed & ITEM_BIT) != 0,
                (packed & QUANTITY_BIT) != 0);
    }
}
