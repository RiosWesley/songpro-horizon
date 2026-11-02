// Fixed names, paths, transaction codes and OEM error codes.
// Values are measured on the Song Pro 8.8" (BYD DiLink 3.0, SX309); see estudos/tema-cluster-88.md.
// Other firmware versions are checked, not pinned: see Compatibility.
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
    static final String STUB = DESCRIPTOR + "$Stub";
    static final String CALLBACK_STUB = CALLBACK + "$Stub";
    // AutoContainer.sendInfo2(int, byte[]), registerCallback(IContainerCallback) and
    // IContainerCallback.receivedInfo2(int, byte[]), as SX309's framework numbers them.
    static final int TX_SEND_INFO2 = 3;
    static final int TX_REGISTER_CALLBACK = 4;
    static final int TX_RECEIVED_INFO2 = 4;
    static final long MIN_GAP_MS = 16000;                    // the native theme switch throttles at 15 s
    static final String STATE_FILE = "/data/local/tmp/horizon-theme-tool.state";

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
