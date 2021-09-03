package org.bbottema.javasocksproxyserver;

public class Logger {
    protected Class loggerClass;
    public Logger(Class loggerClass){
        this.loggerClass = loggerClass;
    }

    public void error(String str,String... format){
        if (format.length>0){
            str = String.format(str,format);
        }
        print(str);
    }

    public void error(String str,Throwable e){
        error("ExceptionMsg:%s "+str,e.getMessage());
    }

    public void debug(String str,String... format){
        if (format.length>0){
            str = String.format(str,format);
        }
        print(str);
    }
    public void debug(String str,Throwable e){
        debug("ExceptionMsg:%s "+str,e.getMessage());
    }

    public void print(String str) {
        System.out.println("[+] "+loggerClass.getSimpleName()+" "+str);
    }
}
