package com.sk89q.worldguard.neoforge.command;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyCommandSyntaxTest {
    private String translate(String command) { return LegacyCommandSyntax.translate(command,world->world.equals("old world") || world.equals("world") ? "minecraft:overworld" : world); }
    @Test void oldWorldOptionsAndNamespacesTranslateWithoutLosingQuotedFlagValues() {
        assertEquals("rg in minecraft:overworld flag home greeting \"hello world\"",translate("worldguard:rg flag home greeting \"hello world\" -w \"old world\""));
        assertEquals("rg in minecraft:overworld info home",translate("rg -w world info home"));
        assertEquals("rg in minecraft:overworld save",translate("rg save world"));
        assertEquals("stopfire minecraft:overworld",translate("stopfire world"));
    }
    @Test void globalTemplatesAndBothChildRemovalModesHaveDistinctTranslations() {
        assertEquals("rg define-global template Alice",translate("rg define -g template Alice"));
        assertEquals("rg remove parent children",translate("rg remove -f parent"));
        assertEquals("rg remove parent force",translate("rg remove parent -u"));
        assertThrows(IllegalArgumentException.class,()->translate("rg remove parent -f -u"));
    }
    @Test void quotedOptionLikeMessagesRemainTextAndUnrelatedCommandsAreNotRewritten() {
        assertEquals("rg flag home greeting \"-w\"",translate("rg flag home greeting \"-w\""));
        assertEquals("give @s minecraft:stone",translate("give @s minecraft:stone"));
        assertEquals("tellraw @a {\"text\":\"hello world\"}",translate("tellraw @a {\"text\":\"hello world\"}"));
        assertEquals("rg flag home pvp -g nonmembers deny",translate("rg flag home pvp -g nonmembers deny"));
    }
    @Test void memberOptionsAreMovedAfterRegionIdsAndListFiltersRetainTheirMeaning() {
        assertEquals("rg addmember home Alice -n",translate("rg addmember -n home Alice"));
        assertEquals("rg removeowner home -a",translate("rg removeowner -a home"));
        assertEquals("rg list-filtered -p Alice -i spawn -s 2",translate("rg list -p Alice -i spawn -s 2"));
        assertEquals("rg tp home center",translate("rg tp -c home"));
        assertEquals("rg tp home spawn",translate("rg tp home -s"));
    }
    @Test void commandTokenizerDecodesEscapedQuotesAndKeepsMultilineEscapeText() {
        assertEquals("Hello \"Alice\"",CommandWords.parse("\"Hello \\\"Alice\\\"\"").getFirst().text());
        assertEquals("Title\\nSubtitle",CommandWords.parse("\"Title\\nSubtitle\"").getFirst().text());
        assertThrows(IllegalArgumentException.class,()->CommandWords.parse("\"unclosed"));
    }
    @Test void originalSilentCommandsWildcardPlayersAndFlagPagesAreAdapted() {
        assertEquals("god -s @a",translate("god * -s"));
        assertEquals("heal -s Alice",translate("worldguard:heal -s Alice"));
        assertEquals("rg in minecraft:overworld flags-page -p 2 home",translate("rg flags -w world -p 2 home"));
        assertEquals("rg migratedb yaml sql",translate("rg migratedb -y yaml sql"));
        assertEquals("stoplag -s resume",translate("stoplag -cs"));
        assertEquals("stoplag -s -i",translate("stoplag -i -s"));
    }
}
