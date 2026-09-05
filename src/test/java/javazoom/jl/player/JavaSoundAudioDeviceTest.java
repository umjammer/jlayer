/*
 * Copyright (c) 2022 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package javazoom.jl.player;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * factory for MyJavaSoundAudioDevice.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2022/10/15 nsano initial version <br>
 */
public class JavaSoundAudioDeviceTest {

    @Test
    void test() throws Exception {
        FactoryRegistry factoryRegistry = FactoryRegistry.systemRegistry();
        assertEquals(3, factoryRegistry.factories.size()); // default 2 + my 1

        AudioDeviceFactory audioDeviceFactory = factoryRegistry.getFactoriesPriority()[2];
        assertInstanceOf(NullAudioDeviceFactory.class, audioDeviceFactory); // null is the lowest priority
    }

    @DisplayName("Factory registry contains expected factories")
    void testFactoryRegistry() throws Exception {
        FactoryRegistry registry = FactoryRegistry.systemRegistry();
        assertNotNull(registry, "Registry should not be null");

        // Should have at least default factories
        assertTrue(registry.factories.size() >= 2,
                "Should have at least 2 factories (default + null)");

        System.out.println("Registered factories: " + registry.factories.size());

        // Get factories by priority
        AudioDeviceFactory[] prioritized = registry.getFactoriesPriority();
        assertTrue(prioritized.length > 0, "Should have prioritized factories");

        // Last factory should be NullAudioDeviceFactory (lowest priority)
        AudioDeviceFactory lastFactory = prioritized[prioritized.length - 1];
        assertInstanceOf(NullAudioDeviceFactory.class, lastFactory,
                "Null factory should have lowest priority");

        System.out.println("Factory priorities verified");
    }

    @Test
    @DisplayName("Create audio device from registry")
    void testCreateAudioDevice() throws Exception {
        FactoryRegistry registry = FactoryRegistry.systemRegistry();

        // Should be able to create a device (may be NullAudioDevice if no sound hardware)
        AudioDevice device = registry.createAudioDevice();
        assertNotNull(device, "Should create an audio device");

        System.out.println("Created device: " + device.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Create device by factory class")
    void testCreateByFactoryClass() throws Exception {
        FactoryRegistry registry = FactoryRegistry.systemRegistry();

        // Try to create JavaSound device
        try {
            AudioDevice device = registry.createAudioDevice(JavaSoundAudioDeviceFactory.class);
            assertNotNull(device, "Device should not be null");

            if (device instanceof NullAudioDevice) {
                System.out.println("JavaSound not available, got NullAudioDevice");
            } else {
                System.out.println("JavaSound device created: " + device.getClass().getSimpleName());
            }
        } catch (javazoom.jl.decoder.JavaLayerException ex) {
            // Expected if hardware not available
            System.out.println("JavaSound device creation failed (expected on some systems): " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("Factory priority ordering")
    void testFactoryPriorityOrdering() throws Exception {
        FactoryRegistry registry = FactoryRegistry.systemRegistry();
        AudioDeviceFactory[] factories = registry.getFactoriesPriority();

        // Verify ordering is consistent
        assertTrue(factories.length > 0, "Should have factories");

        // First factories should not be Null
        if (factories.length > 1) {
            AudioDeviceFactory first = factories[0];
            assertFalse(first instanceof NullAudioDeviceFactory,
                    "First factory should not be Null factory");
        }

        // Print factory order
        System.out.println("=== Factory Priority Order ===");
        for (int i = 0; i < factories.length; i++) {
            System.out.println((i + 1) + ". " + factories[i].getClass().getSimpleName());
        }
    }
}

