package brs.crypto.hash;

import java.security.Provider;
import java.util.Collections;

public class ShabalProvider extends Provider {

    private static final String NAME = "ShabalProvider";
    private static final double VERSION = 1.0;
    private static final String INFO = "A Shabal-256 MessageDigest provider";

    public ShabalProvider() {
        super(NAME, VERSION, INFO);
        putService(new ShabalService(this));
    }

    private class ShabalService extends Service {
        private ShabalService(Provider provider) {
            super(provider, "MessageDigest", Shabal256.ALGORITHM, Shabal256.class.toString(), Shabal256.ALIASES, Collections.emptyMap());
        }

        @Override
        public Object newInstance(Object constructorParameter) {
            return new Shabal256();
        }
    }
}
