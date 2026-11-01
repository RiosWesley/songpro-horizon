// Fixed names, paths, error codes and the SX309 cohort this tool is allowed to act on.
// Values are measured on the Song Pro 8.8" (BYD DiLink 3.0, SX309); see estudos/tema-cluster-88.md.
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class Policy {
    private Policy() {}

    static final String UI_VERSION = "Di3F_DynastyOceanTranslate_V0001";
    static final String ANDROID_DIR = "/sdcard/.cluster/translate/";
    static final String HOST_DIR = "/data/cells/cell2-rw/data/media/0/.cluster/translate/";
    /** The native matcher is (Di3F_.*_.*)_Translate(.*).rcc; this prefix keeps DashCast's own names apart. */
    static final String NAME_PREFIX = UI_VERSION + "_TranslateHorizon_";
    static final Pattern NAME = Pattern.compile("Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_[0-9a-f]{32}\\.rcc");
    static final long MAX_CARRIER = 4L * 1024 * 1024;       // DashCast's recovery bound
    static final String DESCRIPTOR = "android.os.IAutoContainer";
    static final String CALLBACK = "android.os.IContainerCallback";
    static final String FINGERPRINT = "BYD-AUTO/DiLink3.0/DiLink3.0:10/QKQ1.210910.001/eng.build.20251108.051320:user/release-keys";
    static final long MIN_GAP_MS = 16000;                    // the native theme switch throttles at 15 s
    static final String STATE_FILE = "/data/local/tmp/horizon-theme-tool.state";

    /** Read quickly (a few tens of MB). */
    static final Map<String, String> COHORT = new LinkedHashMap<>();
    /** The two big theme RCCs (256 MB): hashed only before a load. */
    static final Map<String, String> COHORT_HEAVY = new LinkedHashMap<>();
    static {
        COHORT.put("/system/lib64/libBydCluster.so", "9f8a0b269fbee37bad510e8dfbc239b857e00a87663fc9fb7ae39913f86017ca");
        COHORT.put("/system/lib64/libBydDataSource.so", "46c367844f6dee435485107d83f709f69cb7d7cd9f84bb1cf40c7a38deeb83bf");
        COHORT.put("/system/lib64/commonTranslate.rcc", "b41bd89a9e3a881c5514992af7033a062cb6207355f809384f0469205d27fcc3");
        COHORT.put("/system/bin/fissiond", "31d39dfc63ffdae993ae5003fc6d392409a1a4c482609cb36f2fcbd8343fd93e");
        COHORT.put("/system/priv-app/XdjaContainerService/XdjaContainerService.apk", "c6d2c097dbbc8a30abf770a0db004b5b2f01c145967f98ab6e47d01c933422d0");
        COHORT.put("/system/etc/container_comm_cfg.json", "792263280d2d8580338dc8381518ea225117dc9dec0574df664b43a4119781ef");
        COHORT.put("/system/framework/framework.jar", "69591c254543461e40cd4c11b8a15ca00224b4fbd46d74e3159a567587115e47");
        COHORT_HEAVY.put("/system/lib64/cluster_theme1.rcc", "7ff2bde70effb76ce06b701c161d7600cd8bd4918c7705a58e30507de67ab009");
        COHORT_HEAVY.put("/system/lib64/cluster_theme2.rcc", "64cda4218526d815036f5f8dce95e6152033e653e58ce061efaf037265bf1795");
    }

    static String errorName(int code) {
        switch (code) {
            case 0x2329: return "RESOURCE_DIR_NOT_EXISTS";
            case 0x232a: return "RESOURCE_DIR_IS_EMPTY";
            case 0x232b: return "RESOURCE_DIR_IS_MULTI_FILES";
            case 0x232c: return "NOT_RCC (RESOURCE_DIR_HAS_NOT_RCC_FILE)";
            case 0x232d: return "UI_VERSION_MISMATCH (RCC_FILE_VERSION_IS_NOT_VERYFIED)";
            case 0x232e: return "RESOURCE_REGISTER_FAILED (RCC_FILE_IS_NOT_A_STANDAR_RESOURCE_FILE)";
            case 0x232f: return "RESOURCE_PATH_IS_EMPTY";
            case 0x2330: return "RESOURCE_IS_SAME";
            case 0x2331: return "RESOURCE_NOT_FOUND (RESOURCE_IS_NOT_EXISTS)";
            case 0x2332: return "GEAR_IS_NOT_P_AND_NOT_INVALID (only switch in P)";
            case 0x2333: return "SET_SKIN_FREQUENCY (too soon after the last change)";
            case 0x2334: return "SKINID_IS_NOT_EXISTS";
            default: return null;
        }
    }
}
