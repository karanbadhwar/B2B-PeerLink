package b2b.controller;

import b2b.service.FileSharer;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.IOUtils;

public class FileController {

    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    // client --> Server (tempDir)
    private final ExecutorService executorService;

    public FileController(int port, String uploadDir) throws IOException
    {
        this.executorService = Executors.newFixedThreadPool(10);
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port),0);
        this.uploadDir = System.getProperty("java.io.tempdir")+ File.separator+"b2bPeerLink-uploads";

        File uploadDirFile = new File(uploadDir);

        if(!uploadDirFile.exists())
        {
            uploadDirFile.mkdir();
        }

        server.createContext("/upload", new UploadHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/", new CORSHandler());
        // Passing the ThreadPool to make the server accept 10 concurrent requests
        server.setExecutor(executorService);
    }

    public void start()
    {
        server.start();
        System.out.println("API server started on port "+server.getAddress().getPort());
    }

    public void stop()
    {
        server.stop(0);
        this.executorService.shutdown();
        System.out.println("API Server stopped");
    }

    private class CORSHandler implements HttpHandler
    {

        // HttpExchange -> This class encapsulates a HTTP request received and a response to be generated in one exchange.
        // It provides methods for examining the request from the client, and for building and sending the response
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Method", "GET, POST, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if(exchange.getRequestMethod().equals("OPTIONS"))
            {
                exchange.sendResponseHeaders(204,-1);
                return;
            }

            String response = "Not Found";
            exchange.sendResponseHeaders(404, response.getBytes().length);
            try (OutputStream oos = exchange.getResponseBody()){
                oos.write(response.getBytes());
            }
        }
    }

    private class DownloadHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

        }
    }

    private class UploadHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            // Method is not POST Method
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST"))
            {
                String response = "Method not allowed";
                headers.add("Content-Type", "text/plain");
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try(OutputStream oos = exchange.getResponseBody()){
                    oos.write(response.getBytes());
                }
                return;
            }

            // ContentType is not  multipart/form-data
            Headers reqHeaders = exchange.getRequestHeaders();
            String contentType = reqHeaders.getFirst("Content-Type");
            if(contentType == null || !contentType.startsWith("multipart/form-data"))
            {
                String response = "Bad Request: Content-Type must be multipart/form-data";
                headers.add("Content-Type", "text/plain");
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try(OutputStream oos = exchange.getResponseBody()){
                    oos.write(response.getBytes());
                }
                return;
            }

            // If both the conditions are true, Parsing the data.
            try{
                // Extracting Boundary, as the Request body is divided by boundary's
                String boundary = contentType.substring(contentType.indexOf("boundary=")+9);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                // Storing the Raw data into out In-memory ByteArrayOutputStream
                IOUtils.copy(exchange.getRequestBody(), baos);

                // Storing now all the Bytes into the Byte[]
                byte[] requestData = baos.toByteArray(); // This is Raw Data or Raw Bytes

                MultiParser parser = new MultiParser(requestData, boundary);


            }catch (Exception ex){

            }
        }
    }

    private static class MultiParser{
        private final byte[] data;

        private final String bounday;

        public MultiParser(byte[] reqData, String boundary)
        {
            this.data = reqData;
            this.bounday = boundary;
        }

        public ParseResult prase()
        {
            String dataAsString = new String(data); // Write now it will only for PDF, Text, CSV... not for MP4 or image
            // Getting file name
            String fileMarker = "filename=\"";
            int fileNameStart = dataAsString.indexOf(fileMarker);
            if(fileNameStart == -1)
            {
                return null;
            }
            int fileNameEnd = dataAsString.indexOf("\",", fileNameStart+=fileMarker.length());
            String fileName = dataAsString.substring(fileNameStart, fileNameEnd);

            // Getting Content type
            String contentTypeMarker = "Content-Type: ";
            int contentTypeStart = dataAsString.indexOf(contentTypeMarker);
        }
    }

    public class ParseResult{
        private final String fileName;

        private final byte[] fileContent;

        public ParseResult(String fileName,byte[] fileContent) {
            this.fileContent = fileContent;
            this.fileName = fileName;
        }
    }

}
