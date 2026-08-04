package example.i1;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

final class SharedSecretFactoryNegative {
    Object failureFactory = SomaSharedSecrets.failureAccess();
}
