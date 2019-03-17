package brs;

import brs.common.TestConstants;
import brs.crypto.hash.ShabalProvider;
import brs.fluxcapacitor.FeatureToggle;
import brs.fluxcapacitor.FluxCapacitor;
import brs.services.TimeService;
import brs.util.Convert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class GeneratorImplTest {

    private GeneratorImpl generator;

    private static final byte[] exampleGenSig = Convert.parseHexString("6ec823b5fd86c4aee9f7c3453cacaf4a43296f48ede77e70060ca8225c2855d0");
    private static final long exampleBaseTarget = 70312;
    private static final int exampleHeight = 500000;

    @Before
    public void setUpGeneratorImplTest() {
        ShabalProvider.init();

        Blockchain blockchain = mock(Blockchain.class);

        TimeService timeService = mock(TimeService.class);

        FluxCapacitor fluxCapacitor = mock(FluxCapacitor.class);
        when(fluxCapacitor.isActive(eq(FeatureToggle.POC2), anyInt())).thenReturn(true);

        generator = new GeneratorImpl(blockchain, timeService, fluxCapacitor);
    }

    @Test
    public void testGeneratorImplCalculateGenerationSignature() {
        byte[] genSig = generator.calculateGenerationSignature(exampleGenSig, TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED);
        assertEquals("ba6f11e2fd1d1eb0a956f92d090da1dd3595c3d888a4ff3b3222c913be6f45b5", Convert.toHexString(genSig));
    }

    @Test
    public void testGeneratorImplCalculateDeadline() {
        BigInteger deadline = generator.calculateDeadline(TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED, 0, exampleGenSig, generator.calculateScoop(exampleGenSig, exampleHeight), exampleBaseTarget, exampleHeight);
        assertEquals(BigInteger.valueOf(7157291745432L), deadline);
    }
}
