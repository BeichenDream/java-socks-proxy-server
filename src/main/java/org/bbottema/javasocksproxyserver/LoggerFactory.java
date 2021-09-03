package org.bbottema.javasocksproxyserver;

public class LoggerFactory {
    public static Class<Logger> loggerClass = Logger.class;
    public static Logger getLogger(Class clszz){
        try {
            return loggerClass.getConstructor(Class.class).newInstance(clszz);
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
