{
  stdenv,
  pkgs,
  lib,
  ...
}:
let
  package_version = "3.8.4";
  runtimeDeps = with pkgs; [ jre ];
  buildDeps = with pkgs; [
    gradle
    makeWrapper
    pkg-config

  ];
  devDeps = with pkgs; [
    jdk
    just
    nushell
  ];

  package = stdenv.mkDerivation (finalAttrs: {
    pname = "signum-node";
    version = package_version;

    src = ./.;

    buildInputs = runtimeDeps;
    nativeBuildInputs = buildDeps ++ devDeps;

    mitmCache = pkgs.gradle.fetchDeps {
      # inherit (finalAttrs) pname;
      pkg = finalAttrs;
      data = ./deps.json;
    };

    __darwinAllowLocalNetworking = true;

    gradleFlags = [
      "-x test"
      "-Dfile.encoding=utf-8"
      "-Pversion=${finalAttrs.version}" # Manually give gradle the version
    ];

    gradleBuildTask = "shadowJar";

    doCheck = true;

    installPhase = ''
      mkdir -p $out/{bin,share}
      cp build/libs/signum-node-$version-all.jar $out/share/signum-node

      makeWrapper ${lib.getExe pkgs.jre} $out/bin/signum-node \
        --add-flags "-jar $out/share/signum-node"
    '';

    meta = {
      homepage = "https://signum.network/";
      description = "Signum Node (formerly Burstcoin)";
      license = lib.licenses.gpl3Only;
      maintainers = with lib.maintainers; [
        "ohager"
        "frankthetank72"
        "damccull"
      ];
      platforms = [ ] ++ lib.platforms.unix;

      sourceProvenance = with pkgs.lib.sourceTypes; [
        fromSource
        binaryBytecode
      ];
    };
  });

  devShell = pkgs.mkShell {
    shellHook = '''';
    LD_LIBRARY_PATH = "${pkgs.stdenv.cc.cc.lib}/lib";
    buildInputs = runtimeDeps;
    nativeBuildInputs = buildDeps ++ devDeps;

  };

in
{
  package = package;
  devShell = devShell;
}
