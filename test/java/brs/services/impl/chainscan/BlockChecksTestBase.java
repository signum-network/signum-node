package brs.services.impl.chainscan;

import brs.Signum;
import brs.common.QuickMocker;
import brs.fluxcapacitor.FluxCapacitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

/** Blocks read the flux capacitor through {@code Signum} when they are constructed. */
abstract class BlockChecksTestBase {

    private MockedStatic<Signum> signum;

    @BeforeEach
    void mockSignum() {
        FluxCapacitor fluxCapacitor = QuickMocker.latestValueFluxCapacitor();
        signum = mockStatic(Signum.class);
        signum.when(Signum::getFluxCapacitor).thenReturn(fluxCapacitor);
    }

    @AfterEach
    void closeSignum() {
        signum.close();
    }
}
