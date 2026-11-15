{
  description = "Tateyomi — a vertical-reading EPUB reader for Android";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "35" ];
        buildToolsVersions = [ "35.0.0" ];
        includeNDK = false;
        includeEmulator = false;
        includeSystemImages = false;
      };

      androidSdk = "${android.androidsdk}/libexec/android-sdk";
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        name = "tateyomi";

        packages = with pkgs; [
          jdk17
          gradle
          android.androidsdk
          unzip
          zip
          git
          curl
          jq
        ];

        JAVA_HOME = "${pkgs.jdk17}";
        ANDROID_HOME = androidSdk;
        ANDROID_SDK_ROOT = androidSdk;
        GRADLE_OPTS = "-Dorg.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8";

        shellHook = ''
          export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
          echo "縦読み devshell"
          echo "  JDK        : $JAVA_HOME"
          echo "  Android SDK: $ANDROID_HOME"
          echo "  build      : gradle assembleDebug"
          echo "  test       : gradle testDebugUnitTest"
        '';
      };

      formatter.${system} = pkgs.nixpkgs-fmt;
    };
}
