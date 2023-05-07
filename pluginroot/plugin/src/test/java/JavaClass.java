import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class JavaClass {

    public static final String pref = "{lineNumber}";

    private static final Log LOGGER = LogFactory.getLog(JavaClass.class);

    public void doit(int x) {
        LOGGER.trace(pref + " -> " + x);
    }


}
