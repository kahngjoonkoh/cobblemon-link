package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MolangTest {
    /** The shapes Cobblemon's dialogues use: concatenation, assignments, nested ternaries, calls. */
    @Test
    void dialogueExpressions() {
        Molang m = new Molang();
        m.funcs.put("q.player.username", a -> "Ash");
        m.funcs.put("q.npc.find_nearby_block", a -> 1.0);
        m.funcs.put("q.player.is_party_at_full_health", a -> false);
        String[] page = {null};
        m.funcs.put("q.dialogue.set_page", a -> page[0] = Molang.text(a.getFirst()));
        assertEquals("Hello, I'm Ash!", m.run("'Hello, I\\'m ' + q.player.username + '!'"));
        m.run("v.healer = c.npc.find_nearby_block('cobblemon:healing_machine'); v.has_healer = v.healer != 0;"
                + "v.full_health = c.player.is_party_at_full_health();"
                + "v.landing_page = !v.has_healer ? 'no_healer' : (v.full_health ? 'full_health' : 'has_charge');");
        assertEquals("has_charge", m.vars.get("landing_page"));
        m.run("q.dialogue.set_page(v.landing_page);");
        assertEquals("has_charge", page[0]);
        assertEquals(0.0, m.run("false ? q.dialogue.close()")); // MoLang's ternary without an else
        assertFalse(Molang.truthy(m.run("q.unknown.thing(1, 'a')")));
        boolean[] closed = {false};
        m.funcs.put("q.dialogue.close", a -> { closed[0] = true; return 0.0; });
        m.funcs.put("q.npc.is_in_battle_with", a -> false);
        m.funcs.put("q.npc.is_doing_activity", a -> true);
        m.run("(q.npc.is_in_battle_with(q.player) || !q.npc.is_doing_activity('minecraft:idle', 'cobblemon:battling')) ? q.dialogue.close()");
        assertFalse(closed[0]); // npc-example's opening: only closes mid-battle
    }
}
