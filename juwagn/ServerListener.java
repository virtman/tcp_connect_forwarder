package juwagn;

import java.net.ServerSocket;
import java.net.InetAddress;
import java.io.IOException;

import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.cert.X509Certificate;

import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;

public class ServerListener {
  private static final int version = 26;

  private static String UserAgent = "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.1; Trident/4.0; .NET CLR 2.0.50727; .NET CLR 3.0.04506.648; .NET CLR 3.5.21022; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729";
                                   //Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; Touch; rv:11.0) like Gecko

  private static int allNum = 0;
  private static synchronized int getNum() {
    return (++ServerListener.allNum);
  }

  private void runMe(ServerSocket SK, String remoteServer, int remotePort, int viassl, String[] subServers, String[] proxy) {
    Socket loopSocket;

    for(;;) {
      try {
        loopSocket = SK.accept();
        if(loopSocket != null) {
          try {
            loopSocket.setTcpNoDelay(true);
          } catch(Exception ed) {
            System.out.println("Socket delay-timeout setting error: " + ed.getMessage());
          }
          try {
            new Thread(new ServerListener.serverProcessor(SK, loopSocket, remoteServer, remotePort, viassl, subServers, proxy)).start();
          } catch(Throwable ed) {
            System.out.println("Thread creating error: " + ed.getMessage());
          }
        } else {
          System.out.println("Error acception socket: null");
          return;
        }
      } catch(Exception asd) {
        System.out.println("Error acception socket: " + asd.getMessage());
        return;
      }
    }
  }

  private static String checkOpts(int i, String[] args) {
    if((i + 1) != args.length) {
      return args[i + 1];
    } else {
      System.out.println("Check your options!");
    }
    return null;
  }

  public static void main(String[] args) {
    int port = 21000;
    String server = "127.0.0.2";

    int remotePort = 0;
    String remoteServer = "";

    String subServer = "";
    String[] subServers = null;

    String[] proxy = null;
    int viassl = 0;

    for(int i = 0; i < args.length; i++) {
      String low = args[i].toLowerCase();
      if(low.equals("-?") || low.equals("/?") || low.equals("/h") || low.equals("-h") || low.equals("-help")) {
        ServerListener.verbose(); //return;
      } else if(low.equals("-ntlm_hash")) {
        String hash = ServerListener.checkOpts(i, args);
        if(hash != null) {
          i++;
          try {
            System.out.println("NT HASH: " + NTLMEngine.ntlmHash(hash));
          } catch(Exception sd) { }
          try {
            System.out.println("LM HASH: " + NTLMEngine.lmHash(hash));
          } catch(Exception sd) { }
        }
      } else if(low.equals("-local")) {
        server = ServerListener.checkOpts(i, args);
        if(server == null) { System.out.println("Check your local server"); return; }
        int tL = server.indexOf(":");
        if(tL > -1) {
          String pr = server.substring(tL + 1);
          server = server.substring(0, tL);
          try {
            port = Integer.parseInt(pr);
            if(port < 1) { System.out.println("Check your local port integer: " + pr); return; }
          } catch(Exception asd) { System.out.println("Check your local port: " + pr); return; }
        } else { System.out.println("Check your local server port"); return; }
        i++;
      } else if(low.equals("-remote")) {
        remoteServer = ServerListener.checkOpts(i, args);
        if(remoteServer == null) { System.out.println("Check your remote server"); return; }
        int tL = remoteServer.indexOf(":");
        if(tL > -1) {
          String pr = remoteServer.substring(tL + 1);
          remoteServer = remoteServer.substring(0, tL);
          try {
            remotePort = Integer.parseInt(pr);
            if(remotePort < 1) { System.out.println("Check your remote port integer: " + pr); return; }
          } catch(Exception asd) { System.out.println("Check your remote port: " + pr); return; }
        } else { System.out.println("Check your remote server port"); return; }
        i++;
      } else if(low.equals("-ssl")) {
        String pr = ServerListener.checkOpts(i, args);
        if(pr == null || pr.length() < 1) { return; }
        if(pr.equalsIgnoreCase("yes") || pr.equalsIgnoreCase("1")) {
          viassl = 1;
          System.out.println("Use ssl between local and remote server");
        } else if(pr.equalsIgnoreCase("2")) {
          System.out.println("Use ssl between local and last sub server");
          viassl = 2;
        } 
        i++;
      } else if(low.equals("-user_agent")) {
        String pr = ServerListener.checkOpts(i, args);
        if(pr == null || pr.length() < 1) { continue; }
        if(pr.endsWith(")")) { pr = pr.substring(0, pr.length() - 1); }
        if(pr.length() < 1) { continue; }
        ServerListener.UserAgent = pr;
        System.out.println("User-Agent: " + pr);
        i++;
      } else if(low.equals("-proxy")) {
        String pr = ServerListener.checkOpts(i, args);
        if(pr == null || pr.length() < 1) { continue; }
        String[] newsuBs = new String[3];
        int tL = pr.lastIndexOf(":");
        
        if(tL > -1) {
          String proxyport = pr.substring(tL + 1);
          pr = pr.substring(0, tL);
          int proxyp = 0;
          String proxyrealm = "";
          try {
            proxyp = Integer.parseInt(proxyport);
            if(proxyp < 1) { System.out.println("Check your proxy port integer: " + proxyport); return; }
          } catch(Exception asd) { System.out.println("Check your proxy port: " + proxyport); return; }
          tL = pr.lastIndexOf("@");
          if(tL > -1) {
            proxyrealm = pr.substring(0, tL);
            pr = pr.substring(tL + 1);
            if(proxyrealm.length() > 0 && proxyrealm.startsWith("(") && proxyrealm.endsWith(")")) {
              int isSep = proxyrealm.indexOf(":");
              if(isSep > -1) { //basisc auth
                if(proxyrealm.substring(0, isSep).indexOf("\\") < 0) {
                  try {                 
                    proxyrealm = ServerListener.encodeBase64(proxyrealm.substring(1, proxyrealm.length() - 1).getBytes("UTF-8")); 
                  } catch(Exception sd) { }
                } else { //ntlm based
                  proxyrealm = proxyrealm.substring(1, proxyrealm.length() - 1);
                }
              }
            } else {
              //base64 encoded!
              try {
                String ifNtlm = new String(ServerListener.decodeBase64(proxyrealm), "UTF-8");
                int isSep = ifNtlm.indexOf(":");
                if(isSep > -1) {
                  if(ifNtlm.substring(0, isSep).indexOf("\\") > -1) { //isntlm
                    proxyrealm = ifNtlm;
                  }
                }
              } catch(Exception ae) { }
            }
          }
          newsuBs[0] = pr;
          newsuBs[1] = "" + proxyp;
          newsuBs[2] = proxyrealm;
          proxy = newsuBs;
        } else { System.out.println("Check your proxy port"); return; }
        i++;
      } else if(low.startsWith("-subserver")) {
        String subServerCheck = ServerListener.checkOpts(i, args);
        if(subServerCheck == null) { System.out.println("Check your subserver"); return; }

        String[] newsuBs = new String[3];

        int tL = subServerCheck.lastIndexOf(":");

        if(tL > -1) {
          String pr = subServerCheck.substring(tL + 1);
          String oneSubserver = subServerCheck.substring(0, tL);
          int subPort;
          String onerealm = "";
          try {
            subPort = Integer.parseInt(pr);
            if(subPort < 1) { System.out.println("Check your subserver port integer: " + pr); return; }
          } catch(Exception asd) { System.out.println("Check your subserver port: " + pr); return; }
          tL = oneSubserver.lastIndexOf("@");
          if(tL > -1) {
            onerealm = oneSubserver.substring(0, tL);
            oneSubserver = oneSubserver.substring(tL + 1);

            if(onerealm.length() > 0 && onerealm.startsWith("(") && onerealm.endsWith(")")) {
              int isSep = onerealm.indexOf(":");
              if(isSep > -1) { //basisc auth
                if(onerealm.substring(0, isSep).indexOf("\\") < 0) {
                  try {                 
                    onerealm = ServerListener.encodeBase64(onerealm.substring(1, onerealm.length() - 1).getBytes("UTF-8")); 
                  } catch(Exception sd) { }
                } else { //ntlm based
                  onerealm = onerealm.substring(1, onerealm.length() - 1);
                }
              }
            } else {
              //base64 encoded!
              try {
                String ifNtlm = new String(ServerListener.decodeBase64(onerealm), "UTF-8");
                int isSep = ifNtlm.indexOf(":");
                if(isSep > -1) {
                  if(ifNtlm.substring(0, isSep).indexOf("\\") > -1) { //isntlm
                    onerealm = ifNtlm;
                  }
                }
              } catch(Exception ae) { }
            }
          }

          newsuBs[0] = oneSubserver;
          newsuBs[1] = "" + subPort;
          newsuBs[2] = onerealm;
          subServer += (onerealm.length() > 0 ? "*@" : "") + oneSubserver + ":" + subPort + " > ";
          
          if(subServers != null) {
            String[] tempArr = new String[subServers.length + 3]; 
            int z = 0;
            for(; z < subServers.length; z++) {
              tempArr[z] = subServers[z];
            }
            tempArr[z++] = newsuBs[0];
            tempArr[z++] = newsuBs[1];
            tempArr[z++] = newsuBs[2];
            subServers = tempArr;
          } else {
            subServers = newsuBs;
          }
        } else { System.out.println("Check your subserver port"); continue; }

        subServers = newsuBs;
        i++;
      } 
    }

    if(subServer.length() > 3) { subServer = subServer.substring(0, subServer.length() - 3); }
    if(remotePort < 1 || remoteServer.length() < 1) { System.out.println("Check your remote server and port"); return; }

    InetAddress serverAddress = null;
    ServerSocket SK;

    if(!server.equals("0.0.0.0")) {
      try {
        serverAddress = InetAddress.getByName(server);
      } catch(Exception as) {
        System.out.println("Local server name can not be used: " + server); return;
      }
    }

    try {
      if(serverAddress != null) {
        SK = new ServerSocket(port, 65535, serverAddress);
      } else {
        SK = new ServerSocket(port, 65535);
      }
    } catch(Exception as) {
      System.out.println("Local port already busy: " + server + ":" + port); return;
    }

    System.out.println("Server started> " + server + ":" + port + (proxy != null ? " >>> " + (proxy[2].length() > 0 ? "*@" : "") + proxy[0] + ":" + proxy[1] : "") + (viassl == 1 ? " >SSL> " : " >> ") + remoteServer + ":" + remotePort + (subServer.length() > 0 ? " > " + subServer : ""));
    
    new ServerListener().runMe(SK, remoteServer, remotePort, viassl, subServers, proxy);  
  }

  private static void closeAllSocks(Socket a, Socket b) {
    if(a != null) {
      try { a.close(); } catch(Exception sda) { } 
    } 
    if(b != null) {
      try { b.close(); } catch(Exception sda) { } 
    }
  }

  /*private static void makeString(byte[] a, int len, String from, String fileNum) {
    byte[] b = new byte[len];
    for(int i = 0; i < len; i++) {
      b[i] = a[i];
    }
    //from += new String(b);
    from = new String(b) + "@!@";
    //if(from.length() > 400) { from = from.substring(0, 400); }
    System.out.println(len + ": " +from + "@!@");
    fileNum = "subpath\\" + fileNum + ".txt";
    try {
      File file = new File(fileNum);
      if(!file.exists()) {
        file.createNewFile();
      }
      FileWriter fileWritter = new FileWriter(fileNum, true);
      BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
      bufferWritter.write(from);
      bufferWritter.close(); 
      fileWritter.close();
    } catch(Exception sd) { 
      sd.printStackTrace(System.out);
    }  
  } */

  private static class remoteProcessor implements Runnable {
    Socket loopSocket;
    Socket remoteSocket;
    InputStream sk_in_remote;
    String fileNum;

    protected remoteProcessor(Socket loopSocket, Socket remoteSocket, InputStream sk_in_remote, String fileNum) {
      this.loopSocket = loopSocket;
      this.remoteSocket = remoteSocket;
      this.sk_in_remote = sk_in_remote;
      this.fileNum = fileNum;
    }

    public void run() { 
      Socket sk = this.loopSocket;
      Socket remote = this.remoteSocket;
      InputStream sk_in = this.sk_in_remote;

      this.loopSocket = null;
      this.remoteSocket = null;
      this.sk_in_remote = null;

      try {
        OutputStream sk_out_loop = sk.getOutputStream();
        byte[] byteholder = new byte[131072];
        //System.out.println("Remote loop try first reading!");
        int readBytes = sk_in.read(byteholder, 0, 131072);
        //System.out.println("Remote loop read: " + readBytes);
        while(readBytes > 0) {
          //ServerListener.makeString(byteholder, readBytes, "\r\nRECEIVED FROM REMOTE: \r\n", this.fileNum);
          sk_out_loop.write(byteholder, 0, readBytes);
          sk_out_loop.flush();
          readBytes = sk_in.read(byteholder, 0, 131072);
          //System.out.println("Remote loop read: " + readBytes);
        }
      } catch(Exception ad) { 
        System.out.println("Error in reading remote loop: " + ad.getMessage());
      }

      ServerListener.closeAllSocks(sk, remote);
      sk = null;
      remote = null;
    }   
  }

  private static long checkHexChunk(byte[] body, int[] overhead) {
    long t = -1;    
    if(body.length < 1) { return t; }
    String num = "";
    int r = 0;
    String toLook = "0123456789ABCDEFabcdef"; 
    String x = "";
    while(r < body.length) {
      x = "" + (char)body[r];
      r++;
      if(toLook.indexOf(x) < 0) {
        if(r < body.length && x.equals("\r") && ("" + (char)body[r]).equals("\n")) { //full data read
          break;
        } else {
          return t; //still not enough data, since need /n
        }
      } 
      num += x;                     
    }

    if(num.length() < 1) { return t; }
    try {
      t = Long.parseLong(num, 16) + (num.length() + 4); //1F0\r\n .... \r\n
    } catch(Exception sd) { t = -1; }

    if(body.length > t) { //still remaining data, possibly "0\r\n\r\n"
      r = body.length;
      int b = r - (int)t;
      if(b == 5) { //no more overhead
        if(body[r - 5] == (byte)'0' && 
          body[r - 4] == (byte)'\r' && 
          body[r - 3] == (byte)'\n' &&          
          body[r - 2] == (byte)'\r' && 
          body[r - 1] == (byte)'\n') {
          t += 5; //just to avoid reloop
        }
      } else if(b > 5) {
        b = b - 5; //how many bytes overhead
        if(body[r - 5 - b] == (byte)'0' && 
          body[r - 4 - b] == (byte)'\r' && 
          body[r - 3 - b] == (byte)'\n' &&          
          body[r - 2 - b] == (byte)'\r' && 
          body[r - 1 - b] == (byte)'\n') {
          t += 5; //just to avoid reloop
          overhead[0] = b;
        } //resultiing body.length will be bigger than t       
      }
    } else if(body.length == t) { //body.length should be at least 5 bytes bigger
      //still expecting end "0\r\n\r\n"
      t += 5; //will reread, since body buffer will be smaller
    }
    return t;
  }

  private static class serverProcessor implements Runnable {
    Socket loopSocket;
    ServerSocket parentSocket;
    String remoteServer;
    int remotePort;
    int viassl;
    String[] subServers; 
    String[] proxy;

    protected serverProcessor(ServerSocket parentSocket, Socket loopSocket, String remoteServer, int remotePort, int viassl, String[] subServers, String[] proxy) {
      this.loopSocket = loopSocket;
      this.parentSocket = parentSocket;
      this.remoteServer = remoteServer;
      this.remotePort = remotePort;
      this.viassl = viassl;
      this.subServers = subServers;
      this.proxy = proxy;
    }
    
    public void run() {
      Object forSyncWait = new Object();
      String fileNum = "con_" + ServerListener.getNum();
      Socket sk = this.loopSocket;
      ServerSocket skParent = this.parentSocket;
      Socket remote = null;
      this.loopSocket = null;
      this.parentSocket = null;
      InputStream sk_in;

      try {
        sk_in = sk.getInputStream();
      } catch(Exception ed) {
        ServerListener.closeAllSocks(sk, remote);
        System.out.println("sk.getInputStream error: " + ed.getMessage());
        return;
      }  
      OutputStream sk_out_remote = null;
      int subVersionDummy = ((int)((Math.random() * 98765) + 1));

      byte[] byteholder = new byte[131072];
      int readBytes = 0;
      try {
        //first remote socket initialisation
        sk.setSoTimeout(5000);
        readBytes = sk_in.read(byteholder, 0, 1); //1 byte readed
        if(readBytes > 0) { //1 byte read here
          sk.setSoTimeout(0); //reset to zero!! 
          readBytes = sk_in.available();
          if(readBytes > 0) {
            readBytes = sk_in.read(byteholder, 1, readBytes);
            readBytes += 1;
          } else {
            readBytes = 1;
          }
          //first chunk of data retrieved, can now start remote listener

          try {
            remote = ServerListener.getRemoteSocket(skParent, this.remoteServer, this.remotePort, this.viassl, subVersionDummy, this.proxy, forSyncWait);
            if(remote == null) { ServerListener.closeAllSocks(sk, null); System.out.println("Remote socket creating error: " + remoteServer + ":" + remotePort); return; }
            try {
              sk_out_remote = remote.getOutputStream();
              if(sk_out_remote == null) { ServerListener.closeAllSocks(sk, remote); System.out.println("Remote socket output stream creating error: " + remoteServer + ":" + remotePort); return; }
            } catch(Exception sdo) {
              ServerListener.closeAllSocks(sk, remote);
              System.out.println("Remote socket output stream creating error: " + remoteServer + ":" + remotePort); 
              return;
            }

            InputStream sk_in_remote = null;
            try {
              sk_in_remote = remote.getInputStream();

              if(this.subServers != null && this.subServers.length > 2) { //at least len 3                 
                for(int xx = 0; xx < this.subServers.length; xx += 3) {
                  ///now create first CONNECT REQUEST for approval connect to subserver 
                  if(!ServerListener.negotiateProxy(sk_out_remote, sk_in_remote, this.subServers[xx + 2], subVersionDummy, this.subServers[xx], this.subServers[xx + 1], (this.remoteServer + ":" + this.remotePort))) { 
                    ServerListener.closeAllSocks(sk, remote);
                    return; 
                  } 
                }
                System.out.println("Begin real data transfer via subserver: " + readBytes);
              } else {
                System.out.println("Begin real data transfer direct: " + readBytes);
              }
              ///
            } catch(Exception sdu) { 
              ServerListener.closeAllSocks(sk, remote);
              System.out.println("First chunk writing to remote socket error: " + remoteServer + ":" + remotePort); 
              return;
            }
            if(remote == null || sk_out_remote == null) { ServerListener.closeAllSocks(sk, remote); return; } //exit if remote socket was not initalised

            if(viassl == 2) { //ssl on latest target instance! NOT DEFAULT!
              Socket remoteOriginal = remote;
              try {
                remote = ServerListener.negotiateSSL(skParent, remote);
                if(remote == null) { System.out.println("Remote ssl socket output stream creating error: " + remoteServer + ":" + remotePort); ServerListener.closeAllSocks(sk, null); return; }
                sk_out_remote = remote.getOutputStream();
                sk_in_remote = remote.getInputStream();
                if(sk_out_remote == null || sk_in_remote == null) { ServerListener.closeAllSocks(sk, remote); System.out.println("Remote ssl socket output stream creating error 2: " + remoteServer + ":" + remotePort); return; }
              } catch(Exception sda) { 
                ServerListener.closeAllSocks(sk, remoteOriginal);
                System.out.println("Error creating ssl socket 2: " + sda.getMessage());
                sda.printStackTrace(System.out);  
                return;
              }
            } 
            //ServerListener.makeString(byteholder, readBytes, "\r\nRECEIVED FROM LOCAL FIRST: \r\n", fileNum);
            sk_out_remote.write(byteholder, 0, readBytes); //that is first chunk of mstsc.exe data! Write after succeed approval of CONNECT request
            sk_out_remote.flush();
            System.out.println("First data written and flushed: " + readBytes + " > available: " + sk_in_remote.available());

            new Thread(new ServerListener.remoteProcessor(sk, remote, sk_in_remote, fileNum)).start();
            remote.setSoTimeout(0); //reset timeout on remote socket!!!
          } catch(Throwable ed) {
            ServerListener.closeAllSocks(sk, remote);
            System.out.println("Thread creating error: " + ed.getMessage());
            ed.printStackTrace(System.out);
            return;
          }             
        } else {
          System.out.println("Data read timeout in initial main loop socket!");
          ServerListener.closeAllSocks(sk, remote); 
          return;
        }
        if(remote == null || sk_out_remote == null) { System.out.println("Remote connection not established A"); ServerListener.closeAllSocks(sk, remote); return; } //exit if remote socket was not initalised

        readBytes = sk_in.read(byteholder, 0, 131072);
        //System.out.println("Local loop read: " + readBytes);
        while(readBytes > 0) {
          //ServerListener.makeString(byteholder, readBytes, "RECEIVED FROM LOCAL: \r\n", fileNum);
          sk_out_remote.write(byteholder, 0, readBytes);
          sk_out_remote.flush();
          readBytes = sk_in.read(byteholder, 0, 131072);
          //System.out.println("Local loop read: " + readBytes);
        }
      } catch(Exception sd) {
        System.out.println("Error in reading local loop: " + sd.getMessage());
      }
      ServerListener.closeAllSocks(sk, remote);
      remote = null;
      sk = null;
    }
  }

  private static Object[] getHeaders(InputStream sk_in_remote) throws Exception { 
    boolean expectHeader = true; 
    byte[] byteholderRemote = new byte[1];

    int readBytesRemote = sk_in_remote.read(byteholderRemote); //1 byte readed
    if(readBytesRemote < 1) {
      System.out.println("Data read timeout in proxy: " + readBytesRemote); 
      return null;
    }
    byte oldByte = byteholderRemote[0];
    readBytesRemote = sk_in_remote.available();
    if(readBytesRemote > 0) {            
      byteholderRemote = new byte[1 + readBytesRemote];
      byteholderRemote[0] = oldByte;
      readBytesRemote = sk_in_remote.read(byteholderRemote, 1, readBytesRemote);
    } 

    if(readBytesRemote == 0) { 
      byteholderRemote = new byte[1];
      byteholderRemote[0] = oldByte;
    }

    if(!((readBytesRemote + 1) == byteholderRemote.length)) {
      System.out.println("Connect proxy method error loop in bytes amount: " + readBytesRemote + ":" + byteholderRemote.length); 
      return null; 
    }
    Object[] ret = new Object[5];

    //check now connect header for OK
    String CONNCheck;
    String CONNOrig;
    String CONNCheckHeader;
    String CONNOrigHeader;
    int lookHeaderSeparator;
    
    for(;;) {
      lookHeaderSeparator = -1; 
      CONNCheck = new String(byteholderRemote, "Cp1252");
      CONNOrig = CONNCheck;
      CONNCheck = CONNCheck.toLowerCase();

      CONNCheckHeader = CONNCheck;
      CONNOrigHeader = CONNOrig;                             

      if(expectHeader) {
        if(CONNOrig.startsWith("HTTP/1.")) { 
          expectHeader = false; 
        } else {
          System.out.println("Unable to get expected header start 3!"); 
          return null;
        }
       
        lookHeaderSeparator = CONNCheck.indexOf("\r\n\r\n");
        if(lookHeaderSeparator > -1) {
          CONNCheckHeader = CONNCheckHeader.substring(0, lookHeaderSeparator + 2);
          CONNOrigHeader = CONNOrigHeader.substring(0, lookHeaderSeparator + 2);
        } else {
          expectHeader = true;
          //continue reading, header not full!!!
          byte[] byteholderRemoteExpect = new byte[1];

          readBytesRemote = sk_in_remote.read(byteholderRemoteExpect); //1 byte readed
          if(readBytesRemote > 0) {
            oldByte = byteholderRemoteExpect[0];
            readBytesRemote = sk_in_remote.available();
            if(readBytesRemote > 0) {                                                    
              byteholderRemoteExpect = new byte[1 + readBytesRemote];
              byteholderRemoteExpect[0] = oldByte;
              readBytesRemote = sk_in_remote.read(byteholderRemoteExpect, 1, readBytesRemote);      
            } 

            if(readBytesRemote == 0) { 
              byteholderRemote = new byte[1];
              byteholderRemote[0] = oldByte;
            }

            if((readBytesRemote + 1) == byteholderRemoteExpect.length) {
              byte[] temp = new byte[byteholderRemote.length + byteholderRemoteExpect.length];
              int x = 0;
              while(x < byteholderRemote.length) { temp[x] = byteholderRemote[x++]; }
              for(int y = 0; y < byteholderRemoteExpect.length;) { temp[x++] = byteholderRemoteExpect[y++]; }
              byteholderRemote = temp;
              continue;
            } else {
              System.out.println("Unable to get two break lines in header!"); 
              return null;
            }
          } else {
            System.out.println("Data read proxy timeout in break lines!"); 
            return null;
          }
        }                         
      }
      break;
    }

    if(expectHeader) { //because already readed
      //now expect chunk or not!
      System.out.println("Header still not recognized 3!");
      return null; 
    }  

    if(lookHeaderSeparator < 0) {
      System.out.println("Unable to get two break lines in header 2!"); 
      return null;
    }
    lookHeaderSeparator += 4;

    byte[] bodyRemote = null; 
    try {
      bodyRemote = new byte[(byteholderRemote.length - lookHeaderSeparator)];
      //System.out.println("ORIG BODY SIZE:" + CONNOrig.substring(lookHeaderSeparator).length());

      for(int s = lookHeaderSeparator, u = 0; s < byteholderRemote.length;) {
        bodyRemote[u++] = byteholderRemote[s++];
      }
    } catch(Exception sd) {
      System.out.println("Unable to get two break lines while getting byte array in header 2!"); 
      return null;
    }

    int lookChunked = CONNCheckHeader.indexOf("\r\ntransfer-encoding: chunked"); //expect more data

    if(lookChunked > -1) {
      //int maxLoop = 0;
      int[] overHeadArr = new int[1];
      for(;;) {   
        overHeadArr[0] = 0;
        long bodyNum = ServerListener.checkHexChunk(bodyRemote, overHeadArr);
        //System.out.println((++maxLoop) + " BODY LEN PROXY OF CHUNKED: " + bodyNum + ":" + bodyRemote.length + "\r\n" + CONNOrigHeader +  "\r\nBODY\r\n" + new String(bodyRemote));
        if(bodyNum < 0 || bodyRemote.length < 1 || bodyRemote.length < bodyNum) { //body not yet retrieved? 
          byte[] byteholderRemoteExpect = new byte[1];

          readBytesRemote = sk_in_remote.read(byteholderRemoteExpect); //1 byte readed
          if(readBytesRemote > 0) {
            oldByte = byteholderRemoteExpect[0];
            readBytesRemote = sk_in_remote.available();
            if(readBytesRemote > 0) {                                  
              byteholderRemoteExpect = new byte[1 + readBytesRemote];
              byteholderRemoteExpect[0] = oldByte;
              readBytesRemote = sk_in_remote.read(byteholderRemoteExpect, 1, readBytesRemote);
            } 

            if(readBytesRemote == 0) { 
              byteholderRemoteExpect = new byte[1];
              byteholderRemoteExpect[0] = oldByte;
            }

            if((readBytesRemote + 1) == byteholderRemoteExpect.length) {
              byte[] temp = new byte[bodyRemote.length + byteholderRemoteExpect.length];
              int x = 0;
              while(x < bodyRemote.length) { temp[x] = bodyRemote[x++]; }
              for(int y = 0; y < byteholderRemoteExpect.length;) { temp[x++] = byteholderRemoteExpect[y++]; }
              bodyRemote = temp;
              continue;
            } else {
              System.out.println("Unable to get body chunk numbers proxy!"); 
              return null;
            } 
          } else {
            System.out.println("Data read proxy timeout in body chunk numbers 2!"); 
            return null;
          } 
        } else if(bodyRemote.length > bodyNum && bodyNum > 0) { //overhead in body, possibly next chunk?   
          if(overHeadArr[0] < 1) { //assume next chunk expected
            byte[] temp = new byte[bodyRemote.length - (int)bodyNum];           
            int x = 0, xb = (int)bodyNum;
            while(x < bodyRemote.length) { temp[x++] = bodyRemote[xb++]; }
            bodyRemote = temp;
            continue;
          } else { //got stop header, but still remaining data    
            int overHead = overHeadArr[0];
            int overHeadBegin = bodyRemote.length - overHead;
            if(overHeadBegin > -1) {
              byte[] temp = new byte[overHead];
              for(int xa = 0; xa < temp.length; xa++) {
                temp[xa] = bodyRemote[overHeadBegin++];
              }
              ret[4] = (Object)temp;
              System.out.println("Unexpected proxy overhead from chunk \r\n" + new String(temp));
            }
          }
        }
        break;
      }
    } else {
      lookChunked = CONNCheckHeader.indexOf("\r\ncontent-length"); //expect more data
      if(lookChunked > -1) {
        String contentLen = CONNCheckHeader.substring(lookChunked + 14); 
        lookChunked = contentLen.indexOf("\r\n");
        if(lookChunked > -1) {
          contentLen = contentLen.substring(0, lookChunked); 
          while(contentLen.endsWith(" ")) { contentLen = contentLen.substring(0, contentLen.length() - 1); }
          lookChunked = contentLen.indexOf(" ");
          if(lookChunked > -1) {
            contentLen = contentLen.substring(lookChunked + 1); 
            while(contentLen.startsWith(" ")) { contentLen = contentLen.substring(1); }
            long bodyNum = -1;
            try { 
              bodyNum = Long.parseLong(contentLen);
            } catch(Exception s) { bodyNum = -1; }
            if(bodyNum > 0) {
              int dataRetrieved = bodyRemote.length;
              for(;;) {
                if(dataRetrieved < bodyNum) { //body not yet retrieved? 
                  byte[] byteholderRemoteExpect = new byte[1];

                  readBytesRemote = sk_in_remote.read(byteholderRemoteExpect); //1 byte readed
                  if(readBytesRemote > 0) {
                    oldByte = byteholderRemoteExpect[0];
                    readBytesRemote = sk_in_remote.available();
                    if(readBytesRemote > 0) {                                  
                      byteholderRemoteExpect = new byte[1 + readBytesRemote];
                      byteholderRemoteExpect[0] = oldByte;
                      readBytesRemote = sk_in_remote.read(byteholderRemoteExpect, 1, readBytesRemote);
                    } 

                    if(readBytesRemote == 0) { 
                      byteholderRemoteExpect = new byte[1];
                      byteholderRemoteExpect[0] = oldByte;
                    }

                    if((readBytesRemote + 1) == byteholderRemoteExpect.length) {
                      dataRetrieved += byteholderRemoteExpect.length;
                      bodyRemote = byteholderRemoteExpect; //store always last chunk of data
                      continue;
                    } else {
                      System.out.println("Unable to get body content-length numbers proxy!"); 
                      return null;
                    } 
                  } else {
                    System.out.println("Data proxy read timeout in getting body content-length numbers 2!"); 
                    return null;
                  } 
                } else if(dataRetrieved > bodyNum && bodyRemote.length > 0) {
                  //unexpected data overhead
                  int overHead = dataRetrieved - (int)bodyNum;
                  int overHeadBegin = bodyRemote.length - overHead;
                  if(overHeadBegin > -1) {
                    byte[] temp = new byte[overHead];
                    for(int xa = 0; xa < temp.length; xa++) {
                      temp[xa] = bodyRemote[overHeadBegin++];
                    }
                    ret[4] = (Object)temp;
                    System.out.println("Unexpected proxy overhead from Content-Length: \r\n" + new String(temp));
                  }
                }
                break;
              }
            }
          }
        }
      }
    }
    ret[0] = (Object)CONNCheck;
    ret[1] = (Object)CONNOrig;
    ret[2] = (Object)CONNCheckHeader;
    ret[3] = (Object)CONNOrigHeader;
    return ret;
  }

  private static Object[] getNTLM(InputStream sk_in_remote, OutputStream sk_out_remote, Object[] ret, String user, String password, String userDomain, String toWrite1, String toWrite3) throws Exception { 
    if(ret == null) { return null; }
    String CONNCheck = (String)ret[0];
    String CONNOrig = (String)ret[1];
    String CONNCheckHeader = (String)ret[2];
    String CONNOrigHeader = (String)ret[3];

    System.out.println("Got ntlm proxy response \r\n" + CONNOrig + "\r\n");
    ////
    String[] headers = CONNCheckHeader.split("\r\n");
    
    int lookNonce = -1; //CONNCheckHeader.indexOf("proxy-authenticate");
    String trimLine = "";

    for(int d = 1; d < headers.length; d++) { //1 = first line
      lookNonce = headers[d].indexOf("proxy-authenticate");
      if(lookNonce > -1) {
        lookNonce = headers[d].indexOf(" ntlm ", lookNonce + 19);
        if(lookNonce > -1) {
          String[] headersOrig = CONNOrigHeader.split("\r\n");
          if(headersOrig.length == headers.length) {
            trimLine = headersOrig[d].substring(lookNonce + 6);
            break;
          } else { lookNonce = -1; }
        }
      }
    }
    if(lookNonce < 0) {
      for(int d = 1; d < headers.length; d++) { //1 = first line
        lookNonce = headers[d].indexOf("www-authenticate");
        if(lookNonce > -1) {
          lookNonce = headers[d].indexOf(" ntlm ", lookNonce + 17);
          if(lookNonce > -1) {
            String[] headersOrig = CONNOrigHeader.split("\r\n");
            if(headersOrig.length == headers.length) {
              trimLine = headersOrig[d].substring(lookNonce + 6);
              break;
            } else { lookNonce = -1; }
          }
        }
      }      
    }

    if(lookNonce < 0) {
      System.out.println("Authenticate proxy ntlm header not found!");  
      return null;
    }

    while(trimLine.startsWith(" ")) { trimLine = trimLine.substring(1); }
    while(trimLine.endsWith(" ")) { trimLine = trimLine.substring(0, trimLine.length() - 1); }

    if(trimLine.length() < 1) {
      System.out.println("Authentication proxy header unexpected, no NTLM response: " + trimLine); 
      return null;
    } 
    ///
    String authNonce;
    try {
      authNonce = NTLMEngine.getResponseFor(trimLine, user, password, null, userDomain);
    } catch(Exception s) {
      authNonce = null;
      System.out.println("Error nonce: " + s.getMessage());
    }    

    if(authNonce == null || authNonce.length() < 1) {
      System.out.println("Unable to send NTLM nonce!"); 
      return null;
    }

    toWrite1 = toWrite1 + "Proxy-Authorization: NTLM " + authNonce + "\r\n" + toWrite3;
    System.out.println("\r\nWrite NTLM nonce header to proxy! \r\n" + toWrite1 + "\r\n");
    sk_out_remote.write((toWrite1).getBytes("Cp1252"));
    sk_out_remote.flush();

    return ServerListener.getHeaders(sk_in_remote);
  }

  private static boolean negotiateProxy(OutputStream sk_out_remote, InputStream sk_in_remote, String subRealm, int subVersionDummy, String subServer, String subPort, String remoteServer) throws Exception {
    boolean ntlm = false;
    String host = subServer; //"HOST";
    String hostDomain = ""; //"HOSTDOMAIN";
    String user = "";
    String userDomain = ""; //"USERDOMAIN";
    String password = "";
    String toWrite2 = "";
    String toWrite3 = "";

    String toWrite1 = "CONNECT " + subServer + ":" + subPort + " HTTP/1.0" + "\r\n"; 

    if(subRealm != null && subRealm.length() > 0) {
      int ntlmFound = subRealm.indexOf("\\");
      if(ntlmFound < 0) {
        toWrite2 = "Proxy-Authorization: Basic " + subRealm;
      } else {
        ntlm = true;
        userDomain = subRealm.substring(0, ntlmFound);
        subRealm = subRealm.substring(ntlmFound + 1);
        ntlmFound = subRealm.indexOf(":");
        if(ntlmFound > -1) {
          user = subRealm.substring(0, ntlmFound);
          password = subRealm.substring(ntlmFound + 1);
        } else {
          user = subRealm;
        }
        toWrite2 = "Proxy-Authorization: NTLM " + NTLMEngine.getResponseFor(null, user, password, null/*host*/, userDomain); 
      }
      while(toWrite2.endsWith("\r\n")) { toWrite2 = toWrite2.substring(0, toWrite2.length() - 2); }
      toWrite2 += "\r\n";
    }

    toWrite3 = "User-Agent: " + ServerListener.UserAgent + "" + subVersionDummy + ")" + "\r\n";
    toWrite3 += "Proxy-Connection: " + "Keep-Alive" + "\r\n";
    toWrite3 += "Content-Length: " + "0" + "\r\n";
    toWrite3 += "Host: " + (remoteServer != null ? remoteServer : subServer + ":" + subPort) + "\r\n";
    toWrite3 += "Pragma: " + "no-cache" + "\r\n";
    toWrite3 += "\r\n";
    System.out.println("\r\nWrite CONNECT header to proxy! \r\n" + (toWrite1 + toWrite2 + toWrite3) + "\r\n");
    sk_out_remote.write((toWrite1 + toWrite2 + toWrite3).getBytes("Cp1252"));
    sk_out_remote.flush();

    ///////
    Object[] ret = ServerListener.getHeaders(sk_in_remote); 
    if(ret == null) { return false; }
    String CONNCheck = (String)ret[0];
    String CONNOrig = (String)ret[1];
    String CONNCheckHeader = (String)ret[2];
    String CONNOrigHeader = (String)ret[3];

    if(ntlm) { //extra handling!
      ret = ServerListener.getNTLM(sk_in_remote, sk_out_remote, ret, user, password, userDomain, toWrite1, toWrite3);
      if(ret == null) { return false; }
      CONNCheck = (String)ret[0];
      CONNOrig = (String)ret[1];
      CONNCheckHeader = (String)ret[2];
      CONNOrigHeader = (String)ret[3];
    } 
    System.out.println("Got proxy response \r\n" + CONNOrig + "\r\n");

    if(!CONNCheck.startsWith("http/1.0 200 ") && !CONNCheck.startsWith("http/1.1 200 ")) {
      if(CONNCheck.startsWith("http/1.0 407 ") || CONNCheck.startsWith("http/1.1 407 ")) {
        System.out.println("Connect proxy method requires authentication: " + CONNOrig.substring(0, Math.min(40, CONNOrig.length()))); 
      } else {
        System.out.println("Connect proxy method - response header unexpected: " + CONNOrig.substring(0, Math.min(40, CONNOrig.length()))); 
      }
    } else {
      if(CONNOrig.indexOf("Unauthorized") > -1) {
        System.out.println("Connect proxy method requires authentication, however received 200 OK!"); 
      } else {
        return true; //here is everything fine
      }
    }    

    return false;
  }

  private static Socket getRemoteSocket(ServerSocket parent, String remoteServer, int remotePort, int viassl, int subVersionDummy, String[] proxy, Object forSyncWait) {
    Socket remote = new Socket();
    Socket remoteOriginal = remote;
    try {
      if(proxy != null) {
        System.out.println("Proxy connect: " + proxy[0] + ":" + proxy[1]);
        remote.connect(new InetSocketAddress(proxy[0], Integer.parseInt(proxy[1])), 10000);  //timeout 10 seconds
        remote.setSoTimeout(5000); //reset timeout on remote socket!!!
        try {                                   
          if(!ServerListener.negotiateProxy(remote.getOutputStream(), remote.getInputStream(), proxy[2], subVersionDummy, remoteServer, ("" + remotePort), null)) { 
            remote = null; 
          }
        } catch(Exception sda) { 
          System.out.println("Error creating proxified socket: " + sda.getMessage());
          sda.printStackTrace(System.out);
          ServerListener.closeAllSocks(remoteOriginal, null);
          return null;
        }
      } else {
        remote.connect(new InetSocketAddress(remoteServer, remotePort), 10000);  //timeout 10 seconds
        remote.setSoTimeout(5000); //reset timeout on remote socket!!!
      }

      if(viassl == 1 && remote != null) {
        try {
          remote = ServerListener.negotiateSSL(parent, remote);
        } catch(Exception sda) { 
          ServerListener.closeAllSocks(remoteOriginal, null);
          System.out.println("Error creating ssl socket: " + sda.getMessage());
          sda.printStackTrace(System.out);
          return null;
        }
      }
      if(remote != null) { return remote; }
    } catch(Exception sd) { }
    ServerListener.closeAllSocks(remoteOriginal, null);
    return null;
  }

  public static TrustManager[] trustAllCerts = new TrustManager[] {
    new X509TrustManager() {
      public X509Certificate[] getAcceptedIssuers() {
        return null;
      }
      public void checkClientTrusted(X509Certificate[] certs, String authType) { }
      public void checkServerTrusted(X509Certificate[] certs, String authType) { }
    }
  };

  private static String[] enabledSSLProtocols = null;

  private static Socket negotiateSSL(ServerSocket parent, Socket sock) throws Exception {
     System.out.println("Negotiate SSL socket: " +  sock.getInetAddress().getHostAddress() + ":" + sock.getPort());

     SSLContext sc = SSLContext.getInstance("SSL");
     java.security.SecureRandom ps = null;
     try {
       ps = java.security.SecureRandom.getInstance("SHA1PRNG");
     } catch(Exception sd) { 
       ps = new java.security.SecureRandom();
     }  

     sc.init(null, ServerListener.trustAllCerts, ps);
     SSLSocketFactory sslSocketFactory = sc.getSocketFactory();

     SSLSocket ss = (SSLSocket)sslSocketFactory.createSocket(sock, sock.getInetAddress().getHostAddress(), sock.getPort(), true);
     ss.setUseClientMode(true);

     String enprot[] = ServerListener.enabledSSLProtocols;
     if(enprot == null) {
       synchronized(parent) {
         enprot = ServerListener.enabledSSLProtocols;
         if(enprot == null) {
           enprot = ss.getEnabledProtocols();
           String listsup = "";
           
           for(int i = 0; i < enprot.length; i++) { //disable SSLv2Hello for exact browser simulation
             if((enprot[i].toUpperCase()).indexOf("SSLv2Hello".toUpperCase()) < 0) {
               listsup += enprot[i] + ",";
             }
           }
           enprot = (listsup.substring(0, listsup.length() - 1)).split(",");
           ServerListener.enabledSSLProtocols = enprot;
         }
       }
     }

     ss.setEnabledProtocols(enprot);
     
     javax.net.ssl.SSLSession sess = ss.getSession();

     java.security.cert.Certificate cert = sess.getPeerCertificates()[0];
     if(cert.getPublicKey() instanceof java.security.interfaces.RSAPublicKey) {
       java.security.interfaces.RSAPublicKey rpk = (java.security.interfaces.RSAPublicKey)cert.getPublicKey();
       System.out.println("Encryption: " + sess.getCipherSuite() + " > RSA key size: " + rpk.getModulus().bitLength() + " " + sess.getProtocol());
     } else { 
       System.out.println("Encryption: " + sess.getCipherSuite() + " " + sess.getProtocol());
     }
     
     return ss;
  }

  private static final char[] CA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
  private static int[] IA = new int[256];
  static {       
    for(int i = 0; i < IA.length; i++) { IA[i] = -1; }
    for(int i = 0, iS = CA.length; i < iS; i++) { IA[CA[i]] = i; }
    IA['='] = 0;
  }

  public final static String encodeBase64(byte[] sArr) {
    // Check special case
    int sLen = sArr != null ? sArr.length : 0;
    if(sLen == 0) { return ""; }
    int eLen = (sLen / 3) * 3;              // Length of even 24-bits.
    int cCnt = ((sLen - 1) / 3 + 1) << 2;   // Returned character count
    int dLen = cCnt; // Length of returned array
    char[] dArr = new char[dLen];

    // Encode even 24-bits
    for(int s = 0, d = 0, cc = 0; s < eLen;) {
      int i = (sArr[s++] & 0xff) << 16 | (sArr[s++] & 0xff) << 8 | (sArr[s++] & 0xff);

      // Encode the int into four chars
      dArr[d++] = CA[(i >>> 18) & 0x3f];
      dArr[d++] = CA[(i >>> 12) & 0x3f];
      dArr[d++] = CA[(i >>> 6) & 0x3f];
      dArr[d++] = CA[i & 0x3f];
    }

    // Pad and encode last bits if source isn't even 24 bits.
    int left = sLen - eLen; // 0 - 2.
    if(left > 0) {
      // Prepare the int
      int i = ((sArr[eLen] & 0xff) << 10) | (left == 2 ? ((sArr[sLen - 1] & 0xff) << 2) : 0);

      dArr[dLen - 4] = CA[i >> 12];
      dArr[dLen - 3] = CA[(i >>> 6) & 0x3f];
      dArr[dLen - 2] = left == 2 ? CA[i & 0x3f] : '=';
      dArr[dLen - 1] = '=';
    } 
    return new String(dArr);
  }

  public final static byte[] decodeBase64(String str) {
    int sLen = str != null ? str.length() : 0;
    if(sLen == 0)
      return new byte[0];

    int sepCnt = 0; 
    for(int i = 0; i < sLen; i++) {
      if(IA[str.charAt(i)] < 0) {
        sepCnt++;
      }
    }
    // Check so that legal chars (including '=') are evenly divideable by 4 as specified in RFC 2045.
    if((sLen - sepCnt) % 4 != 0) {
      return null;
    }
    // Count '=' at end
    int pad = 0;
    for(int i = sLen; i > 1 && IA[str.charAt(--i)] <= 0;) {
      if(str.charAt(i) == '=') {
        pad++;
      }
    }
    int len = ((sLen - sepCnt) * 6 >> 3) - pad;

    byte[] dArr = new byte[len];       // Preallocate byte[] of exact length

    for(int s = 0, d = 0; d < len;) {
      // Assemble three bytes into an int from four "valid" characters.
      int i = 0;
      for (int j = 0; j < 4; j++) {   // j only increased if a valid char was found.
        int c = IA[str.charAt(s++)];
        if(c >= 0) {
          i |= c << (18 - j * 6);
        } else {
          j--;
        }
      }
      // Add the bytes
      dArr[d++] = (byte) (i >> 16);
      if(d < len) {
        dArr[d++]= (byte) (i >> 8);
        if(d < len) {
          dArr[d++] = (byte) i;
        }
      }
    }
    return dArr;
  }

  public static void verbose() {
    String a = "";

    a += "\r\nBy juwagn@gmail.com version: " + ServerListener.version + "\r\nPossible run parameters: ";

    a += "\r\n\r\n-local 127.0.0.2:21000";
    a += "\r\n(the server listener, by 0.0.0.0:21000 it will listen on all interfaces)";

    a += "\r\n\r\n-remote 33.87.132.11:80";
    a += "\r\n(if -subserver given, remote server must support http connect proxy else will just forward traffic as is)";

    a += "\r\n\r\n-ssl yes or -ssl 1";
    a += "\r\n(ssl will be established before creating cascading subserver connection)";
    a += "\r\n-ssl 2";
    a += "\r\n(ssl will be established after creating cascading subserver connection, if -subserver not given equals -ssl 1)";

    a += "\r\n\r\n-user_agent ";
    a += "\r\n(set own user agent of any browser)";

    a += "\r\n\r\n-subserver 10.10.8.205:3389";
    a += "\r\nor";
    a += "\r\n-subserver 10.10.8.205:80 -subserverNext 10.10.8.201:3389";
    a += "\r\n(it means multi-cascading via http connect proxy)";
    a += "\r\nor";
    a += "\r\n-subserver bXlsb2dvbjpteXBhc3M=@10.10.8.205:3389 -subserverNext bXlsb2dvbjpteXBhc3M=@10.10.8.201:3389";
    a += "\r\n(bXlsb2dvbjpteXBhc3M means base64 ut8 encoded Basic or NTLM authentication realm in this example Basic> mylogon:mypass before forwarding connection to target)";
    a += "\r\nor";
    a += "\r\n-subserver (mylogon:mypass)@10.10.8.205:3389 -subserverNext (mylogon:mypass)@10.10.8.201:3389";
    a += "\r\n(same as before, but auth data gets encoded automatically, in this example mylogon:mypass)";
    a += "\r\n(if -subserver not given it will act as direct port forwarder)";

    a += "\r\n\r\n-proxy your.proxy.com:8080";
    a += "\r\nor";
    a += "\r\n-proxy bXlsb2dvbjpteXBhc3M=@your.proxy.com:8080";
    a += "\r\n(proxy or proxy with Basic or NTLM authentication realm base64 ut8 encoded)";
    a += "\r\nor";
    a += "\r\n-proxy (mylogon:mypass)@your.proxy.com:8080";
    a += "\r\n(same as before, but auth data gets encoded automatically, in this example mylogon:mypass)";
    a += "\r\nor";
    a += "\r\n-proxy (Domain/mylogon:mypass)@your.proxy.com:8080";
    a += "\r\n(ntlm based logon with domain)";
    a += "\r\nor";
    a += "\r\n-proxy (/mylogon:mypass)@your.proxy.com:8080";
    a += "\r\n(ntlm based logon without domain)";
    a += "\r\n(the presence of / slash char means always NTLM logon)";

    a += "r\n\\r\n-ntlm_hash mypass";
    a += "\r\n(displays computed NT and LM hashes of passed password to reuse for NTLM auth instead clear password, check below how to reuse it)";
    a += "\r\n------------------------------------------------------------";

    a += "\r\n\r\nReal examples";
    a += "\r\n@java -cp \"%~dp0localwebs.jar\" juwagn.ServerListener -local 127.0.0.2:21000 -proxy (\\j:1)@127.0.0.1:8080 -remote 33.87.132.11:80 -subserver (test:ok)@demo.net:3389 -ssl 1";
    a += "\r\nMeans: 127.0.0.2:21000 > 127.0.0.1:8080 >(ssl)> 33.87.132.11:80 > demo.net:3389 via proxy";

    a += "\r\n\r\n@java -cp \"%~dp0localwebs.jar\" juwagn.ServerListener -local 127.0.0.2:21000 -proxy (\\j:1)@127.0.0.1:8080 -remote 33.87.132.11:80 -subserver (test:ok)@demo.net:3389 -ssl 2";
    a += "\r\nMeans: 127.0.0.2:21000 > 127.0.0.1:8080 > 33.87.132.11:80 >(ssl)> demo.net:3389 via proxy";

    a += "\r\n\r\n@java -cp \"%~dp0localwebs.jar\" juwagn.ServerListener -local 127.0.0.2:21000 -proxy (\\j:1)@127.0.0.1:8080 -remote 33.87.132.11:80";
    a += "\r\nMeans: 127.0.0.2:21000 > 127.0.0.1:8080 > 33.87.132.11:80 direct port forwarder via proxy";

    a += "\r\n\r\n@java -cp \"%~dp0localwebs.jar\" juwagn.ServerListener -local 127.0.0.2:21000 -remote 33.87.132.11:80";
    a += "\r\nMeans: 127.0.0.2:21000 > 33.87.132.11:80 direct port forwarder";

    a += "\r\n\r\n@java -cp \"%~dp0localwebs.jar\" juwagn.ServerListener -local 127.0.0.2:21000 -proxy (\\j:1)@127.0.0.1:8080 -remote 33.87.132.11:80 -subserver (test:ok)@demo.net:80 -subserverNext google.com:443";
    a += "\r\nMeans: 127.0.0.2:21000 > 127.0.0.1:8080 > 33.87.132.11:80 > demo.net:80 > google.com:443 via proxy";

    a += "\r\n\r\n-proxy and -subserver support Basic or NTLM logons";
    a += "\r\n(\\j:1)@ before server:port = NTLM auth";
    a += "\r\n(*\\j:bXlsb2dvbjpteXBhc3M=)@ before server:port = NTLM auth, base64 passed as NT hash, NTLMv2 only, NTLMv1 would request extra LM hash. Domain if necessary should follow after * (*mydomain\\j:bXlsb2dvbjpteXBhc3M=)";
    a += "\r\n(*\\j:bXlsb2dvbjpteXBhc3M=:efOPjfIuWviaV3ZIE7skAQ==)@ before server:port = NTLM auth, base64:base64 passed as NT:LM hash, used for both NTLMv1 and NTLMv2, but by NTLMv2 only NT hash part get's used, LM part not involved";

    a += "\r\n(j:1)@ before server:port = Basic";

    a += "\r\n\r\nPS: if -subserver is given, then -remote server must support CONNECT and -subserver plays the role of final target, if many -subserverNext are given ";
    a += "\r\nthen the latest -subserver* plays the role of final target and -remote + all preceding -subserver* must support CONNECT";
    System.out.println(a);
  }
}