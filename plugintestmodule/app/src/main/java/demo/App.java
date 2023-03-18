package demo;

import org.apache.commons.logging.LogFactory;
import org.slf4j.LoggerFactory;

public class App {
    public static void main(String[] args) {
        System.out.println("hello");
        LoggerFactory.getLogger(App.class).debug("debugging with slf4j");
        LogFactory.getLog(App.class).debug("debugging with commons logging");
    }

}
