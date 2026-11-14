package io.trellis.common.id;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Creates random {@link ReferenceCode}s of one type. Uses a cryptographically strong source by default, so codes
 * cannot be guessed from one another. 60 random bits: collisions are possible in principle (birthday bound around
 * a billion codes per prefix), which is why the column is {@code UNIQUE}.
 */
public final class ReferenceCodeGenerator {
    private final String prefix;
    private final RandomGenerator random;

    /** @param prefix two to four upper-case letters naming the type, e.g. {@code NT} for notices */
    public ReferenceCodeGenerator(String prefix) {
        this(prefix, new SecureRandom());
    }

    /** Tests pass a seeded generator. */
    public ReferenceCodeGenerator(String prefix, RandomGenerator random) {
        this.prefix = ReferenceCode.requirePrefix(prefix);
        this.random = Objects.requireNonNull(random, "random");
    }

    public ReferenceCode next() {
        int length = ReferenceCode.ALPHABET.length();
        StringBuilder code = new StringBuilder(prefix);
        for (int g = 0; g < ReferenceCode.GROUPS; g++) {
            code.append(ReferenceCode.SEPARATOR);
            for (int i = 0; i < ReferenceCode.GROUP_LENGTH; i++) {
                code.append(ReferenceCode.ALPHABET.charAt(random.nextInt(length)));
            }
        }
        return new ReferenceCode(code.toString());
    }
}
