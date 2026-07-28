package io.github.somaruntime.soma.runtime;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Caller-declared String reachability profile。
 *
 * <p>该对象只形成 versioned、unverified estimate；runtime 不读取 String
 * internals，也不维护 identity set。</p>
 */
public final class StringResourceProfile {
    public static final String ESTIMATOR_IDENTITY =
            "soma-string-reference-estimator-v1";
    private static final String HASH_PREFIX =
            "soma-java:v1:string-resource-profile\n";
    private static final StringResourceProfile UNPROFILED =
            new StringResourceProfile();

    private final StringResourceProfileStatus status;
    private final int averageUtf16CodeUnits;
    private final int maximumUtf16CodeUnits;
    private final long valueCardinality;
    private final long distinctObjectIdentityEstimate;
    private final int intraTableSharingBasisPoints;
    private final int interTableSharingBasisPoints;
    private final int presenceBasisPoints;
    private final Set<StringResourceRole> roles;
    private final int simultaneouslyLiveTableCount;
    private final String estimatorIdentity;
    private final long estimatedReachableBytes;
    private final String profileIdentity;

    private StringResourceProfile() {
        status = StringResourceProfileStatus.UNPROFILED;
        averageUtf16CodeUnits = 0;
        maximumUtf16CodeUnits = 0;
        valueCardinality = 0L;
        distinctObjectIdentityEstimate = 0L;
        intraTableSharingBasisPoints = 0;
        interTableSharingBasisPoints = 0;
        presenceBasisPoints = 0;
        roles = Collections.emptySet();
        simultaneouslyLiveTableCount = 0;
        estimatorIdentity = ESTIMATOR_IDENTITY;
        estimatedReachableBytes = 0L;
        profileIdentity = CanonicalSupport.sha256(
                HASH_PREFIX, toCanonicalJson());
    }

    private StringResourceProfile(Builder builder) {
        status = StringResourceProfileStatus.PROFILED_UNVERIFIED;
        averageUtf16CodeUnits = builder.averageUtf16CodeUnits;
        maximumUtf16CodeUnits = builder.maximumUtf16CodeUnits;
        valueCardinality = builder.valueCardinality;
        distinctObjectIdentityEstimate =
                builder.distinctObjectIdentityEstimate;
        intraTableSharingBasisPoints =
                builder.intraTableSharingBasisPoints;
        interTableSharingBasisPoints =
                builder.interTableSharingBasisPoints;
        presenceBasisPoints = builder.presenceBasisPoints;
        roles = Collections.unmodifiableSet(
                EnumSet.copyOf(builder.roles));
        simultaneouslyLiveTableCount =
                builder.simultaneouslyLiveTableCount;
        estimatorIdentity = ESTIMATOR_IDENTITY;
        estimatedReachableBytes = estimateReachableBytes(
                distinctObjectIdentityEstimate, averageUtf16CodeUnits);
        profileIdentity = CanonicalSupport.sha256(
                HASH_PREFIX, toCanonicalJson());
    }

    public static StringResourceProfile unprofiled() {
        return UNPROFILED;
    }

    public static Builder builder() {
        return new Builder();
    }

    public StringResourceProfileStatus status() { return status; }
    public int averageUtf16CodeUnits() { return averageUtf16CodeUnits; }
    public int maximumUtf16CodeUnits() { return maximumUtf16CodeUnits; }
    public long valueCardinality() { return valueCardinality; }
    public long distinctObjectIdentityEstimate() {
        return distinctObjectIdentityEstimate;
    }
    public int intraTableSharingBasisPoints() {
        return intraTableSharingBasisPoints;
    }
    public int interTableSharingBasisPoints() {
        return interTableSharingBasisPoints;
    }
    public int presenceBasisPoints() { return presenceBasisPoints; }
    public Set<StringResourceRole> roles() { return roles; }
    public int simultaneouslyLiveTableCount() {
        return simultaneouslyLiveTableCount;
    }
    public String estimatorIdentity() { return estimatorIdentity; }
    public long estimatedReachableBytes() { return estimatedReachableBytes; }
    public String profileIdentity() { return profileIdentity; }

    String toCanonicalJson() {
        StringBuilder json = new StringBuilder()
                .append('{')
                .append("\"status\":")
                .append(CanonicalSupport.quote(
                        status.name().toLowerCase(Locale.ROOT)));
        if (status == StringResourceProfileStatus.UNPROFILED) {
            return json.append('}').toString();
        }
        json.append(",\"averageUtf16CodeUnits\":")
                .append(averageUtf16CodeUnits)
                .append(",\"distinctObjectIdentityEstimate\":")
                .append(distinctObjectIdentityEstimate)
                .append(",\"estimatedReachableBytes\":")
                .append(estimatedReachableBytes)
                .append(",\"estimatorIdentity\":")
                .append(CanonicalSupport.quote(estimatorIdentity))
                .append(",\"interTableSharingBasisPoints\":")
                .append(interTableSharingBasisPoints)
                .append(",\"intraTableSharingBasisPoints\":")
                .append(intraTableSharingBasisPoints)
                .append(",\"maximumUtf16CodeUnits\":")
                .append(maximumUtf16CodeUnits)
                .append(",\"presenceBasisPoints\":")
                .append(presenceBasisPoints)
                .append(",\"roles\":[");
        boolean first = true;
        for (StringResourceRole role : roles) {
            if (!first) json.append(',');
            json.append(CanonicalSupport.quote(
                    role.name().toLowerCase(Locale.ROOT)));
            first = false;
        }
        return json.append("],\"simultaneouslyLiveTableCount\":")
                .append(simultaneouslyLiveTableCount)
                .append(",\"valueCardinality\":")
                .append(valueCardinality)
                .append('}')
                .toString();
    }

    private static long estimateReachableBytes(
            long identities, int averageCodeUnits) {
        long characterBytes = checkedMultiply(2L, averageCodeUnits);
        long perIdentity = alignEight(checkedAdd(40L, characterBytes));
        return checkedMultiply(identities, perIdentity);
    }

    private static long alignEight(long value) {
        if (value > Long.MAX_VALUE - 7L) {
            throw new IllegalArgumentException(
                    "String resource profile arithmetic overflows");
        }
        return (value + 7L) & ~7L;
    }

    private static long checkedAdd(long left, long right) {
        if (left < 0L || right < 0L || Long.MAX_VALUE - left < right) {
            throw new IllegalArgumentException(
                    "String resource profile arithmetic overflows");
        }
        return left + right;
    }

    private static long checkedMultiply(long left, long right) {
        if (left < 0L || right < 0L
                || left != 0L && right > Long.MAX_VALUE / left) {
            throw new IllegalArgumentException(
                    "String resource profile arithmetic overflows");
        }
        return left * right;
    }

    public static final class Builder {
        private int averageUtf16CodeUnits;
        private int maximumUtf16CodeUnits;
        private long valueCardinality;
        private long distinctObjectIdentityEstimate;
        private int intraTableSharingBasisPoints;
        private int interTableSharingBasisPoints;
        private int presenceBasisPoints = 10000;
        private final EnumSet<StringResourceRole> roles =
                EnumSet.noneOf(StringResourceRole.class);
        private int simultaneouslyLiveTableCount = 1;
        private boolean open = true;

        private Builder() {
        }

        public Builder averageUtf16CodeUnits(int value) {
            requireOpen();
            if (value < 0) {
                throw new IllegalArgumentException(
                        "averageUtf16CodeUnits must be non-negative");
            }
            averageUtf16CodeUnits = value;
            return this;
        }

        public Builder maximumUtf16CodeUnits(int value) {
            requireOpen();
            if (value < 0) {
                throw new IllegalArgumentException(
                        "maximumUtf16CodeUnits must be non-negative");
            }
            maximumUtf16CodeUnits = value;
            return this;
        }

        public Builder valueCardinality(long value) {
            requireOpen();
            if (value < 0L) {
                throw new IllegalArgumentException(
                        "valueCardinality must be non-negative");
            }
            valueCardinality = value;
            return this;
        }

        public Builder distinctObjectIdentityEstimate(long value) {
            requireOpen();
            if (value < 0L) {
                throw new IllegalArgumentException(
                        "distinctObjectIdentityEstimate must be non-negative");
            }
            distinctObjectIdentityEstimate = value;
            return this;
        }

        public Builder intraTableSharingBasisPoints(int value) {
            requireOpen();
            intraTableSharingBasisPoints =
                    requireBasisPoints(value, "intraTableSharingBasisPoints");
            return this;
        }

        public Builder interTableSharingBasisPoints(int value) {
            requireOpen();
            interTableSharingBasisPoints =
                    requireBasisPoints(value, "interTableSharingBasisPoints");
            return this;
        }

        public Builder presenceBasisPoints(int value) {
            requireOpen();
            presenceBasisPoints =
                    requireBasisPoints(value, "presenceBasisPoints");
            return this;
        }

        public Builder role(StringResourceRole value) {
            requireOpen();
            if (value == null) throw new NullPointerException("role");
            roles.add(value);
            return this;
        }

        public Builder simultaneouslyLiveTableCount(int value) {
            requireOpen();
            if (value <= 0) {
                throw new IllegalArgumentException(
                        "simultaneouslyLiveTableCount must be positive");
            }
            simultaneouslyLiveTableCount = value;
            return this;
        }

        public StringResourceProfile build() {
            requireOpen();
            open = false;
            if (maximumUtf16CodeUnits < averageUtf16CodeUnits) {
                throw new IllegalArgumentException(
                        "maximumUtf16CodeUnits must be >= averageUtf16CodeUnits");
            }
            long maximumDistinctObjectIdentities = checkedMultiply(
                    valueCardinality, simultaneouslyLiveTableCount);
            if (distinctObjectIdentityEstimate
                    > maximumDistinctObjectIdentities) {
                throw new IllegalArgumentException(
                        "distinctObjectIdentityEstimate must be <= "
                                + "valueCardinality * simultaneouslyLiveTableCount");
            }
            if (roles.isEmpty()) {
                throw new IllegalArgumentException(
                        "at least one String resource role is required");
            }
            return new StringResourceProfile(this);
        }

        private void requireOpen() {
            if (!open) {
                throw new IllegalStateException(
                        "StringResourceProfile.Builder is closed");
            }
        }

        private static int requireBasisPoints(int value, String name) {
            if (value < 0 || value > 10000) {
                throw new IllegalArgumentException(
                        name + " must be in [0,10000]");
            }
            return value;
        }
    }
}
