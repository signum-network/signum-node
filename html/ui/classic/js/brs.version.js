/**
 * @depends {brs.js}
 */
class PreleaseTag {
    constructor(tag) {
        var priority;
        switch (tag) {
            case "dev":
                priority = 0;
                break;
            case "alpha":
                priority = 1;
                break;
            case "beta":
                priority = 2;
                break;
            case "rc":
                priority = 3;
                break;
            case "":
                priority = 4;
                break;
            default:
                priority = 5;
                break;
        }
        this.priority = priority;
    }
}

class Version {
    constructor(version) {
        version = version.replace("-", ".").toLowerCase();
        if (version.startsWith("v")) version = version.substring(1);
        var tokens = version.split(".");
        this.major = parseInt(tokens[0]);
        this.minor = parseInt(tokens[1]);
        this.patch = parseInt(tokens[2]);
        if (tokens.length > 3) {
            var prereleaseTagAndIteration = tokens[3].split(/([a-z]+)([0-9]+)/).filter(function(s) { return s !== ""; });
            this.prereleaseTag = new PreleaseTag(prereleaseTagAndIteration[0]);
            this.prereleaseIteration = prereleaseTagAndIteration.length === 2 ? parseInt(prereleaseTagAndIteration[1]) : -1;
        } else {
            this.prereleaseTag = new PreleaseTag("");
            this.prereleaseIteration = 0;
        }
    }

    isGreaterThan(otherVersion) {
        if (this.major > otherVersion.major) return true;
        if (this.major < otherVersion.major) return false;
        if (this.minor > otherVersion.minor) return true;
        if (this.minor < otherVersion.minor) return false;
        if (this.patch > otherVersion.patch) return true;
        if (this.patch < otherVersion.patch) return false;
        if (this.prereleaseTag.priority > otherVersion.prereleaseTag.priority) return true;
        if (this.prereleaseTag.priority < otherVersion.prereleaseTag.priority) return false;
        return this.prereleaseIteration > otherVersion.prereleaseIteration;
    };

    isGreaterThanOrEqualTo(otherVersion) {
        if (this.isGreaterThan(otherVersion)) return true;
        return this.equals(otherVersion);
    };

    equals(version) {
        if (this.major !== version.major) return false;
        if (this.minor !== version.minor) return false;
        if (this.patch !== version.patch) return false;
        if (this.prereleaseIteration !== version.prereleaseIteration) return false;
        return this.prereleaseTag.priority === version.prereleaseTag.priority;
    };
}

var BRS = (function(BRS, $, undefined) {
    BRS.versionCompare = function(v1, v2) {
        if (v2 === undefined || v2 === null) {
            return -1;
        }
        else if (v1 === undefined || v1 === null) {
            return -1;
        }
        try {
            var version1 = new Version(v1);
            var version2 = new Version(v2);
            return version1.isGreaterThanOrEqualTo(version2);
        } catch (e) {
            return false;
        }
    };
    return BRS;
}(BRS || {}, jQuery));
