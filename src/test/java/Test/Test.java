package Test;

import common.java.httpServer.booter;
import common.java.nlogger.nlogger;

public class Test {
    public static void main(String[] args) {
        booter booter = new booter();
        try {
            System.out.println("GrapeRight");
            System.setProperty("AppName", "GrapeRight");
            booter.start(1008);
        } catch (Exception e) {
            nlogger.logout(e);
        }
    }
}
