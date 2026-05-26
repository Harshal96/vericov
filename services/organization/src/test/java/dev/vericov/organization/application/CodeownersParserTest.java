package dev.vericov.organization.application;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodeownersParserTest {
    @Test
    void parsesCommentsEscapedSpacesTeamOwnersAndProviderOrdering() {
        List<ParsedCodeownersRule> rules = CodeownersParser.parse("""
                # comments are ignored
                /src/payments/ @acme/payments @octo-user
                docs/Legal\\ Docs/** @acme/legal
                malformed-without-owner
                *.md @acme/docs
                """);

        assertEquals(3, rules.size());
        assertEquals("src/payments/**", rules.get(0).pattern());
        assertEquals(List.of("@acme/payments", "@octo-user"), rules.get(0).owners());
        assertEquals(0, rules.get(0).providerOrder());
        assertEquals("docs/Legal Docs/**", rules.get(1).pattern());
        assertEquals(List.of("@acme/legal"), rules.get(1).owners());
        assertEquals("*.md", rules.get(2).pattern());
        assertEquals(2, rules.get(2).providerOrder());
    }
}
