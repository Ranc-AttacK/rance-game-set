package com.rance.galcommunity.architecture;

import com.rance.galcommunity.GalCommunityApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleStructureTest {

    @Test
    void moduleBoundariesAreValid() {
        ApplicationModules.of(GalCommunityApplication.class).verify();
    }
}
