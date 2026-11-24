package com.bepinex.android.tools;

import java.util.Collections;
import java.util.Map;

/** Runtime paths read by the Android BepInEx native bootstrap. */
public final class FusionConfig {
    public boolean isIl2Cpp2Mono;
    public boolean useOriginalLibUnity;
    public String gamePackageId;
    public String gameLibraryDirectory;
    public String appLibraryDirectory;
    public String appDataDirectory;
    public String codeCacheDirectory;
    public String bepInExDirectory;
    public String dotnetDirectory;
    public String unityDataDirectory;
    public String unityVersion;
    public String[] fusionVariables;
    public String[] auxiliaryPluginFolders;
    public Map<String, String> il2cppApiMap;

    public FusionConfig(String packageId, String libraryDirectory, String appDataDirectory,
                        String codeCacheDirectory, String bepInExDirectory,
                        String dotnetDirectory, String unityDataDirectory, String unityVersion) {
        this.isIl2Cpp2Mono = false;
        this.useOriginalLibUnity = true;
        this.gamePackageId = packageId;
        this.gameLibraryDirectory = libraryDirectory;
        this.appLibraryDirectory = libraryDirectory;
        this.appDataDirectory = appDataDirectory;
        this.codeCacheDirectory = codeCacheDirectory;
        this.bepInExDirectory = bepInExDirectory;
        this.dotnetDirectory = dotnetDirectory;
        this.unityDataDirectory = unityDataDirectory;
        this.unityVersion = unityVersion;
        this.fusionVariables = new String[0];
        this.auxiliaryPluginFolders = new String[0];
        this.il2cppApiMap = Collections.emptyMap();
    }
}
