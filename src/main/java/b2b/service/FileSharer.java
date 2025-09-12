package b2b.service;

import b2b.utils.UploadUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class FileSharer {
    // Integer -> For Port
    // String -> For File Path
    private Map<Integer, String> availableFiles;

    public FileSharer() {
        this.availableFiles = new HashMap<>();
    }

    public int offerFile(String filePath) throws TimeoutException {
        long startTime = System.currentTimeMillis();
        int port;

        while (true) {
            port = UploadUtils.generateCode();

            // Check if port is free
            if (!availableFiles.containsKey(port)) {
                availableFiles.put(port, filePath);
                return port;
            }

            // Check timeout
            if (System.currentTimeMillis() - startTime > 5000) { // 5000 ms = 5 seconds
                throw new TimeoutException("Server is busy , Please try again!");
            }
        }
    }

    public void startFileServer(int port)
    {
        String filePath = availableFiles.get(port);
        if(filePath == null)
        {
            System.out.println("No File is associated with port: "+port);
            return;
        }

        try(ServerSocket socket = new ServerSocket(port))
        {
            System.out.println("Serving file "+new File(filePath).getName() + " on port "+port);
            Socket clientSocket = socket.accept();
            System.out.println("Client connection: "+clientSocket.getInetAddress());
            new Thread(new FileSenderHandler(clientSocket, filePath)).start();
        } catch (IOException ex)
        {
            System.out.println("Error handling file server on port: "+port);
        }
    }

    private static class FileSenderHandler implements Runnable{
        private final Socket clientSocket;
        private final String filePath;

        public FileSenderHandler(Socket clientSocket, String filePath) {
            this.clientSocket = clientSocket;
            this.filePath = filePath;
        }

        @Override
        public void run() {
            try(FileInputStream fis = new FileInputStream(filePath))
            {
                OutputStream oos = clientSocket.getOutputStream();
                String fileName = new File(filePath).getName();
                String header = "FileName: "+fileName+"\n";
                oos.write(header.getBytes());

                byte[] buffer = new byte[4096]; // Assuming right now the file will be till 4096 bytes
                int byteRead;
                while((byteRead = fis.read(buffer)) != -1)
                {
                    oos.write(buffer, 0, byteRead);
                }
                System.out.println("File "+fileName+" sent to "+clientSocket.getInetAddress());


            }catch(IOException e)
            {
                System.out.println("Error sending the file to the client! "+e.getMessage());
            } finally{
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.out.println("Error closing socket "+e.getMessage());
                }
            }
        }
    }

}
