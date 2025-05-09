{
  inputs = {
    nixpkgs = {
      url = "github:nixos/nixpkgs/nixos-unstable";
    };
  };
  outputs =
    inputs@{ nixpkgs, self, ... }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      eachSystem =
        f: inputs.nixpkgs.lib.genAttrs systems (system: f inputs.nixpkgs.legacyPackages.${system});

    in
    {
      packages = eachSystem (
        pkgs:
        let
          signumNodeOutputs = pkgs.callPackage ./package.nix { };
        in
        rec {
          default = signumNode;
          signumNode = signumNodeOutputs.package;
          updateDeps = signumNodeOutputs.updateDeps.mitmCache.updateScript;
        }
      );

      devShells = eachSystem (
        pkgs:
        let
          signumNodeOutputs = pkgs.callPackage ./package.nix { };
        in
        rec {
          default = signumDevShell;
          signumDevShell = signumNodeOutputs.devShell;
        }
      );
    };
}
