package server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.simple.JSONObject;

public class Main {

    static String nameCutter = "Content-Disposition: form-data; name=";
    static String valueCutter = "\r\n\r\n";

    // 서버를 시작하는 부분임. 나중에 죽이려면 server.stop();
    public static void main(String[] args) throws Exception {
        // 8000포트로, /test 컨텍스트로 서비스
        HttpServer server = HttpServer.create(new InetSocketAddress(8001), 0);
        server.createContext("/", new IntroHandler());
        server.start();
    }

    // 실제 서버의 동작은 이와 같이 별도 클래스로 만듬.
    // 여기서는 내부 클래스로 선언했는데, 좀더 복잡하게 만들려면 별도 클래스가 나음.
    static class IntroHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            String method = t.getRequestMethod();
            if (method.equalsIgnoreCase("POST")) {
                Map<String, String> requestBodyMap = getFormdata(t);
                if (path.equalsIgnoreCase("/api/text2triple")) {
                    String response =requestBodyMap.get("url");
                    t.sendResponseHeaders(200, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                } else if (path.equalsIgnoreCase("/api/triple2rdf")) {
                    String response = requestBodyMap.get("triple");
                    t.sendResponseHeaders(200, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                } else if (path.equalsIgnoreCase("/api/rdf2kg")) {
                    JSONObject json = new JSONObject();
                    for( Map.Entry<String, String> entry : requestBodyMap.entrySet() ) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        json.put(key, value);
                    }
                    t.sendResponseHeaders(200, json.toString().length());
                    OutputStream os = t.getResponseBody();
                    os.write(json.toString().getBytes());
                    os.close();
                    return;
                }
            }
            String response = "Not found";
            t.sendResponseHeaders(404, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private Map<String, String> getFormdata(HttpExchange t) {
            try {
                byte[] b = t.getRequestBody().readAllBytes();
                String data = new String(b);
                Map<String, String> requestBodyMap = new HashMap<>();
                while (true) {
                    int nameFirstIdx = data.indexOf(nameCutter) + nameCutter.length() + 1;
                    int nameLastIdx = data.indexOf('"', nameFirstIdx);
                    if (nameFirstIdx == -1 || nameLastIdx == -1) {
                        break;
                    }
                    String name = data.substring(nameFirstIdx, nameLastIdx);

                    int valueFirstIdx = data.indexOf(valueCutter, nameLastIdx) + valueCutter.length();
                    int valueLastIdx = data.indexOf("\r\n", valueFirstIdx);
                    if (valueFirstIdx == -1 || valueLastIdx == -1) {
                        break;
                    }
                    String value = data.substring(valueFirstIdx, valueLastIdx);

                    requestBodyMap.put(name, value);
                    data = data.substring(valueLastIdx);
                }
                return requestBodyMap;
            } catch (IOException e) {
                throw new Error(e);
            }
        }
    }
}
