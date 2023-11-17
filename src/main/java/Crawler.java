import java.net.URLEncoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.util.Map;
import java.io.*;

//Represents a web crawler for the website Fakebook
public class Crawler {

  //Arguments:
  private String server;
  private int port;
  private String username;
  private String password;

  //Data:
  private final Map<String, String> cookies = new HashMap<>();
  private final Queue<String> frontier = new ArrayDeque<>();
  private final Set<String> seen = new HashSet<>();
  private final Set<String> flags = new HashSet<>();

  //Initialize a crawler
  public Crawler(String server, int port, String username, String password) {
    this.server = server;
    this.port = port;
    this.username = username;
    this.password = password;
  }

  //Find the csrf middleware token from a html body
  public String findMiddleWareToken(String body){
    int i = body.indexOf("csrfmiddlewaretoken") + 28;
    return body.substring(i, i+64);
  }

  //Log in to Fakebook and set the session id cookie
  public void login() throws IOException {

    //Build Login Get
    StringBuilder loginGet = new StringBuilder();
    loginGet.append("GET /accounts/login/ HTTP/1.1\r\nHost: www.3700.network\r\nConnection: close\r\n\r\n");
    HTTPResponse responseGet = NetUtil.communicate(this.server, this.port, loginGet.toString());

    //Build Content
    StringBuilder content = new StringBuilder();
    String encodedUsername = URLEncoder.encode(this.username, "UTF-8");
    String encodedPassword = URLEncoder.encode(this.password, "UTF-8");
    content.append("csrfmiddlewaretoken=").append(this.findMiddleWareToken(responseGet.body)).append("&");
    content.append("username=").append(encodedUsername).append("&password=").append(encodedPassword).append("\r\n\r\n");


    //Build Login Post
    StringBuilder loginPost = new StringBuilder();
    loginPost.append("POST /accounts/login/ HTTP/1.1\r\nHost: www.3700.network\r\n");
    loginPost.append("Connection: close\r\n");

    loginPost.append("Cookie:");
    for (Map.Entry<String, String> entry : responseGet.getCookies().entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      loginPost.append(" ").append(key).append("=").append(value).append(";");
    }

    loginPost.append("\r\n");
    loginPost.append("Content-Length: ").append(content.length()-4).append("\r\n");
    loginPost.append("Content-Type: application/x-www-form-urlencoded\r\n");

    loginPost.append("\r\n");

    loginPost.append(content);

    HTTPResponse responsePost = NetUtil.communicate(this.server, this.port, loginPost.toString());

    this.cookies.put("sessionid", responsePost.getCookies().get("sessionid"));
  }

  //Crawl the Fakebook website
  //Login then kickstart thread to crawl Fakebook concurrently
  public void run() {
    try {

      this.login();

      for (int i = 0; i < 8; i++) {
        // Create a thread and pass a Runnable to its constructor
        Thread thread = new Thread(() -> {
          try {
            this.crawl();
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
        // Start the thread
        thread.start();
      }


    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  //Request a webpage and scan its html response for links/flags
  public void scanWebpage(String path) throws Exception {
    StringBuilder get = new StringBuilder();
    get.append("GET ").append(path).append(" HTTP/1.1\r\nHost: www.3700.network\r\n");
    get.append("Connection: close\r\n");
    get.append("Cookie: ").append("sessionid").append("=").append(this.cookies.get("sessionid")).append(";");
    get.append("\r\n\r\n");
    HTTPResponse responseGet = NetUtil.communicate(this.server, this.port, get.toString());

    switch (responseGet.status){
      case "302":
        break;
      case "404":
      case "403":
        break;
      case "503":
      case "504":
        this.frontier.add(path);
        System.out.println(responseGet.raw);
        break;
      case "200":
        this.gatherLinks(responseGet.body);
        this.gatherFlags(responseGet.body);
        break;
      default:
        System.out.println(responseGet.raw);
    }
  }

  //Get the flags from a html body
  public void gatherFlags(String body){
    String[] strs = body.split("class='secret_flag' style=\"color:red\">FLAG: ");
    for(int i=1; i<strs.length; i++){
      String flag = strs[i].substring(0, 64);
      this.flags.add(flag);
      System.out.println(flag);
    }
  }

  //Get viable Fakebook links from the html body
  public void gatherLinks(String body){
    String[] strs = body.split("<a href=\"/fakebook/");

    for(int i=1; i<strs.length; i++){
      String newLink = "/fakebook/" + strs[i].substring(0, strs[i].indexOf("\""));
      if(!this.seen.contains(newLink)){
        this.frontier.add(newLink);
        this.seen.add(newLink);
      }
    }
  }

  //Crawl through webpages until the frontier is exhausted or all 5 flags are found
  public void crawl() throws Exception {

    this.frontier.add("/fakebook/");

    while(!this.frontier.isEmpty() && this.flags.size()<5){
      String nextPath = this.frontier.poll();
      this.scanWebpage(nextPath);
    }

  }

  //Parse command line inputs and run the Crawler
  public static void main(String[] args) {

    String server = "www.3700.network";
    int port = 443;

    int i = 0;

    if(args[i].equals("-s")){
      i++;
      server = args[i];
      i++;
    }

    if(args[i].equals("-p")){
      i++;
      port = Integer.parseInt(args[i]);
      i++;
    }


    String username = args[i];
    i++;
    String password = args[i];

    Crawler crawler = new Crawler(server, port, username, password);
    crawler.run();
  }

}

//Representing a utility class used for HTTPS communication over a TLS socket
class NetUtil {

  //Creates a TLS socket connected to the given server and port
  //Sends the request data over the socket and reads the response, returning it as a HTTPResponse
  public static HTTPResponse communicate(String hostname, int port, String request) throws IOException {
    SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    SSLSocket sock = (SSLSocket) sslSocketFactory.createSocket(hostname, port);
    sock.startHandshake();

    sendOutput(sock, request);
    return readInput(sock);
  }

  //Send the data over the given TLS socket
  public static void sendOutput(SSLSocket sock, String data) throws IOException {
//    System.out.println("Sending:\n" + data);
    OutputStream out = sock.getOutputStream();
    byte[] byteMessage = data.getBytes(StandardCharsets.UTF_8);
    out.write(byteMessage);
    out.flush();
  }

  //Read the input off the given TLS socket and create an HTTPRequest from it
  public static HTTPResponse readInput(SSLSocket sock) throws IOException {

    BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();

    try  {
      String line;
      while((line = br.readLine()) != null){
        sb.append(line).append("\n");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

//    System.out.println("Received: \n" + sb);

    return new HTTPResponse(sb.toString());
  }

}

//Represents a response received from a server over http
class HTTPResponse{

  public final Map<String, String> header = new HashMap<>();
  public final Map<String, String> cookies = new HashMap<>();
  public String body;
  public String status;
  public String raw;

  //Parses the information of the response from the string
  public HTTPResponse(String response) {
    //Store raw data
    this.raw = response;

    String[] lines = response.split("\n");

    //Status:
    String[] statusLine = lines[0].split(" ");
    this.status = statusLine[1];

    //Header:
    int i = 1;
    while(i<lines.length && !lines[i].equals("")){
      String[] subLine = lines[i].split(":");
      String key = subLine[0];
      String value = subLine[1].substring(1);

      if(key.equals("set-cookie")){
        this.extractCookie(value);
      } else{
        this.header.put(key, value);
      }

      i++;
    }

    //Body:
    StringBuilder sb = new StringBuilder();
    for(int n=i; n<lines.length; n++){
      sb.append(lines[n]).append("\n");
    }
    this.body = sb.toString();

  }

  //Extract the cookies from the header argument and store them
  private void extractCookie(String str){
    String[] split = str.split("=");
    String key = split[0];
    String value = split[1].substring(0, split[1].indexOf(";"));
    this.cookies.put(key, value);
  }

  //Returns the map of cookies
  public Map<String, String> getCookies(){
    return this.cookies;
  }


}
