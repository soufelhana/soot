package com.soot.analyzer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soot.G;
import soot.options.Options;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SootInitTest {

    @Test
    @DisplayName("Should initialize Soot 4.5.0 options without error")
    void testSootInitialization() {
        assertDoesNotThrow(() -> {
            G.reset();
            Options.v().set_allow_phantom_refs(true);
            Options.v().set_prepend_classpath(true);
            Options.v().set_keep_line_number(true);
            Options.v().set_whole_program(false);

            String currentClasspath = System.getProperty("java.class.path");
            Options.v().set_soot_classpath(currentClasspath);
        });

        assertTrue(Options.v().allow_phantom_refs());
        assertTrue(Options.v().keep_line_number());
    }

    public static void main(String[] args) {
        System.out.println("Testing Soot 4.5.0 initialization on Java " + System.getProperty("java.version"));
        new SootInitTest().testSootInitialization();
        System.out.println("Soot options configured successfully.");
    }
}
