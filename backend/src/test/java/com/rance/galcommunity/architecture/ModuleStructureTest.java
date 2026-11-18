package com.rance.galcommunity.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.rance.galcommunity.GalCommunityApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class ModuleStructureTest {

    private static final ApplicationModules MODULES =
            ApplicationModules.of(GalCommunityApplication.class);

    @Test
    void detectsExpectedModules() {
        assertThat(MODULES.stream().map(ApplicationModule::getName))
                .containsExactlyInAnyOrder("shared", "identity", "community");
    }

    @Test
    void moduleBoundariesAreValid() {
        MODULES.verify();
    }
}
