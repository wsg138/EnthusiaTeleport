package org.enthusia.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

final class PluginSurfaceContractTest {
    private static final Set<String> EXPECTED_COMMANDS = Set.of(
            "tpa", "tpask", "tpahere", "tpaccept", "tpyes", "tpadeny", "tpno", "tpacancel", "tpignore",
            "sethome", "home", "homes", "delhome", "bed", "spawn", "tppos", "tpo", "invsee", "inventorysee",
            "endersee", "enderview", "rtp", "top", "back", "eteleport", "ahome"
    );

    @Test
    void commandSurfaceAndProviderContractStayExplicit() throws IOException {
        Path pluginYml = repositoryRoot().resolve("src/main/resources/plugin.yml");
        String raw = Files.readString(pluginYml);
        Map<String, Map<String, String>> commands = parseSection(Files.readAllLines(pluginYml), "commands");

        assertEquals(new TreeSet<>(EXPECTED_COMMANDS), new TreeSet<>(commands.keySet()));
        assertTrue(raw.contains("depend: [CombatLogX]"), "CombatLogX is a hard runtime dependency and must remain explicit");
        assertTrue(raw.contains("softdepend: [NewPlayerProtection]"), "NewPlayerProtection soft integration must remain explicit");
        assertTrue(raw.contains("api-version: \"1.21\""));

        commands.forEach((name, fields) -> {
            assertTrue(!fields.getOrDefault("description", "").isBlank(), name + " needs an operator-visible description");
            assertTrue(!fields.getOrDefault("usage", "").isBlank(), name + " needs usage documentation");
            assertTrue(!fields.getOrDefault("permission", "").isBlank(), name + " must have an explicit permission boundary");
        });
    }

    @Test
    void everyCommandPermissionIsDeclaredAndAdministrativeCapabilitiesStayFailClosed() throws IOException {
        Path pluginYml = repositoryRoot().resolve("src/main/resources/plugin.yml");
        var lines = Files.readAllLines(pluginYml);
        Map<String, Map<String, String>> commands = parseSection(lines, "commands");
        Map<String, Map<String, String>> permissions = parseSection(lines, "permissions");

        commands.forEach((name, fields) -> {
            String permission = fields.get("permission");
            assertTrue(permissions.containsKey(permission), name + " references undeclared permission " + permission);
        });

        Set<String> privileged = Set.of(
                "enthusia.teleport.admin", "enthusia.teleport.tppos", "enthusia.teleport.tpo",
                "enthusia.teleport.invsee", "enthusia.teleport.invsee.edit", "enthusia.teleport.endersee",
                "enthusia.teleport.endersee.edit", "enthusia.teleport.top", "enthusia.teleport.back",
                "enthusia.teleport.bypass-combat", "enthusia.teleport.bypass-teleport",
                "enthusia.teleport.bypass-world-block", "enthusia.teleport.admin.reload",
                "enthusia.teleport.admin.homes.teleport", "enthusia.teleport.admin.homes.view",
                "enthusia.teleport.admin.homes.delete"
        );
        privileged.forEach(permission -> {
            assertTrue(permissions.containsKey(permission), permission + " must remain declared");
            assertEquals("op", permissions.get(permission).get("default"), permission + " must remain operator-only by default");
        });

        assertEquals("false", permissions.get("enthusia.teleport.rtp").get("default"), "RTP stays explicit opt-in");
    }

    private static Map<String, Map<String, String>> parseSection(java.util.List<String> lines, String section) {
        Map<String, Map<String, String>> entries = new LinkedHashMap<>();
        boolean inside = false;
        String current = null;
        for (String line : lines) {
            if (line.equals(section + ":")) {
                inside = true;
                current = null;
                continue;
            }
            if (!inside) continue;
            if (!line.isBlank() && !line.startsWith(" ")) break;
            if (line.startsWith("  ") && !line.startsWith("    ") && line.trim().endsWith(":")) {
                current = line.trim().substring(0, line.trim().length() - 1);
                entries.put(current, new LinkedHashMap<>());
                continue;
            }
            if (current != null && line.startsWith("    ")) {
                String trimmed = line.trim();
                int split = trimmed.indexOf(':');
                if (split > 0) {
                    entries.get(current).put(trimmed.substring(0, split).trim(), unquote(trimmed.substring(split + 1).trim()));
                }
            }
        }
        return entries;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))) return parent;
        throw new IllegalStateException("Could not locate EnthusiaTeleport repository root from " + current);
    }
}
