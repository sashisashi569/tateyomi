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
        # エミュレータ検証用（AGENTS.md「動作確認の分担」）。
        # "default" = AOSP イメージ（Google API 不要・サイズ小）。
        includeEmulator = true;
        includeSystemImages = true;
        systemImageTypes = [ "default" ];
        abiVersions = [ "x86_64" ];
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
          # cmdline-tools（avdmanager）はXDG規約で ~/.config/.android/avd にAVDを
          # 書くが、emulator 本体は $ANDROID_AVD_HOME しか探さない。両者を揃える。
          export ANDROID_AVD_HOME="''${ANDROID_AVD_HOME:-$HOME/.config/.android/avd}"
          export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$PATH"
          echo "縦読み devshell"
          echo "  JDK        : $JAVA_HOME"
          echo "  Android SDK: $ANDROID_HOME"
          echo "  emulator   : $(command -v emulator || echo なし)"
          echo "  build      : gradle assembleDebug"
          echo "  test       : gradle testDebugUnitTest"
        '';
      };

      formatter.${system} = pkgs.nixpkgs-fmt;
    };
}
