package brs;

import java.util.Objects;
import java.util.StringTokenizer;

public final class Version {
    public static final Version EMPTY = new Version(0, 0, 0, PrereleaseTag.NONE, -1);

    private final int major;
    private final int minor;
    private final int patch;
    private final PrereleaseTag prereleaseTag; // NONE if release
    private final int prereleaseIteration; // -1 if release

    public Version(int major, int minor, int patch, PrereleaseTag prereleaseTag, int prereleaseIteration) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prereleaseTag = prereleaseTag;
        this.prereleaseIteration = prereleaseIteration;
    }

    public static Version parse(String version) throws IllegalArgumentException {
        System.out.println("Parsing " + version);
        try {
            version = version.replace("-", ".").toLowerCase();
            if (version.startsWith("v")) version = version.substring(1);
            StringTokenizer tokenizer = new StringTokenizer(version, ".", false);
            int major = Integer.parseInt(tokenizer.nextToken());
            int minor = Integer.parseInt(tokenizer.nextToken());
            int patch = Integer.parseInt(tokenizer.nextToken());
            if (tokenizer.hasMoreTokens()) {
                String[] prereleaseTagAndIteration = tokenizer.nextToken().split("(?<=[a-z])(?=[0-9])");
                PrereleaseTag prereleaseTag = PrereleaseTag.withTag(prereleaseTagAndIteration[0]);
                int prereleaseIteration = prereleaseTagAndIteration.length == 2 ? Integer.parseInt(prereleaseTagAndIteration[1]) : -1;
                return new Version(major, minor, patch, prereleaseTag, prereleaseIteration);
            } else {
                return new Version(major, minor, patch, PrereleaseTag.NONE, -1);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Version not formatted correctly", e);
        }
    }

    public boolean backwardsCompatibilityNeeded() { // TODO remove once Constants.MIN_VERSION is greater than 2.3.0
        return (major < 2 || (major == 2 && minor < 3));
    }

    public String toBackwardsCompatibleStringIfNeeded(Version peerVersion) { // TODO remove once Constants.MIN_VERSION is greater than 2.3.0
        // Old peers do not understand new versioning system. It is supported from version major=2,minor=3
        if (peerVersion.backwardsCompatibilityNeeded()) {
            return toBackwardsCompatibleString();
        } else {
            return toString();
        }
    }

    public String toBackwardsCompatibleString() { // TODO remove once Constants.MIN_VERSION is greater than 2.3.0
        return major + "." + minor + "." + patch;
    }

    @Override
    public String toString() {
        String baseVersion = "v"+major+"."+minor+"."+patch;
        return prereleaseTag == PrereleaseTag.NONE ? baseVersion : baseVersion+"-"+prereleaseTag.tag+(prereleaseIteration >= 0 ? prereleaseIteration : "");
    }

    public boolean isPrelease() {
        return prereleaseTag != PrereleaseTag.NONE;
    }

    public boolean isGreaterThan(Version otherVersion) {
        if (major > otherVersion.major) return true;
        if (major < otherVersion.major) return false;
        if (minor > otherVersion.minor) return true;
        if (minor < otherVersion.minor) return false;
        if (patch > otherVersion.patch) return true;
        if (patch < otherVersion.patch) return false;
        if (prereleaseTag.priority > otherVersion.prereleaseTag.priority) return true;
        if (prereleaseTag.priority < otherVersion.prereleaseTag.priority) return false;
        return prereleaseIteration > otherVersion.prereleaseIteration;
    }

    public boolean isGreaterThanOrEqualTo(Version otherVersion) {
        if (isGreaterThan(otherVersion)) return true;
        return Objects.equals(this, otherVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Version version = (Version) o;

        if (major != version.major) return false;
        if (minor != version.minor) return false;
        if (patch != version.patch) return false;
        if (prereleaseIteration != version.prereleaseIteration) return false;
        return prereleaseTag == version.prereleaseTag;
    }

    @Override
    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + patch;
        result = 31 * result + prereleaseTag.hashCode();
        result = 31 * result + prereleaseIteration;
        return result;
    }

    public enum PrereleaseTag {
        DEVELOPMENT("dev", 0),
        ALPHA("alpha", 1),
        BETA("beta", 2),
        RC("rc", 3),
        NONE("", 4),
        ;

        private final String tag;
        private final int priority;

        PrereleaseTag(String tag, int priority) {
            this.tag = tag;
            this.priority = priority;
        }

        public static PrereleaseTag withTag(String tag) throws IllegalArgumentException {
            for(PrereleaseTag prereleaseTag : values()) {
                if (Objects.equals(prereleaseTag.tag, tag)) {
                    return prereleaseTag;
                }
            }
            throw new IllegalArgumentException("Provided does not match any prelease tags");
        }
    }
}
