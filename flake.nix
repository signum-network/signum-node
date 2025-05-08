{
  inputs = {
    nixpkgs = {
      url = "github:nixos/nixpkgs/nixos-unstable";
    };
    flake-parts.url = "github:hercules-ci/flake-parts";
  };
  outputs =
    inputs:
    inputs.flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [ "x86_64-linux" ];
      perSystem =
        {
          config,
          self',
          pkgs,
          lib,
          system,
          ...
        }:
        let
          runtimeDeps = with pkgs; [
          ];
          buildDeps = with pkgs; [
            gradle
            pkg-config
          ];
          devDeps = with pkgs; [
            jdk
            just
            nushell
          ];

          javaPackage = pkgs.stdenv.mkDerivation (finalAttrs: {
            pname = "signum-node";
            version = "3.8.4";

            src = ./.;

            nativeBuildInputs = buildDeps ++ devDeps;

            mitmCache = pkgs.gradle.fetchDeps {
              # inherit (finalAttrs) pname;
              pkg = finalAttrs;
              data = ./deps.json;
            };

            __darwinAllowLocalNetworking = true;

            gradleFlags = [
              "-Dfile.encoding=utf-8"
              "-Pversion=${finalAttrs.version}" # Manually give gradle the version
            ];

            gradleBuildTask = "shadowJar";

            doCheck = true;

            installPhase = ''
              mkdir -p $out/{bin,share/my-package}
              cp build/libs/signum-node.jar $out/share/signum-node

              makeWrapper ${lib.getExe pkgs.jre} $out/bin/signum-node \
                --add-flags "-jar $out/share/signum-node/signum-node-all.jar"
            '';

            meta.sourceProvenance = with pkgs.lib.sourceTypes; [
              fromSource
              binaryBytecode
            ];
          });

          mkDevShell =
            inputs:
            pkgs.mkShell {
              shellHook = '''';
              LD_LIBRARY_PATH = "${pkgs.stdenv.cc.cc.lib}/lib";
              buildInputs = runtimeDeps;
              nativeBuildInputs = buildDeps ++ devDeps ++ [ inputs ];
            };
        in
        {
          _module.args.pkgs = import inputs.nixpkgs {
            inherit system;
            overlays = [ ];
            config.allowUnfreePredicate =
              pkg:
              builtins.elem (lib.getName pkg) [
              ];
          };

          packages.default = javaPackage;
          packages.updateDeps = javaPackage.mitmCache.updateScript;

          devShells.default = mkDevShell "";

        };
    };
}
