import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Запуск сервера в отдельном потоке
        new Thread(() -> Server.startServer()).start();

        // Запуск клиента
        SwingUtilities.invokeLater(Client::new);
    }
}

class Server {
    private static final int PORT = 12345;
    private static List<PrintWriter> clients = new ArrayList<>();

    public static void startServer() {
        System.out.println("Сервер запущен...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                synchronized (clients) {
                    clients.add(out);
                }

                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Получено сообщение: " + message);
                    logMessage(message);
                    synchronized (clients) {
                        for (PrintWriter client : clients) {
                            client.println(message);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                synchronized (clients) {
                    clients.remove(out);
                }
            }
        }

        private void logMessage(String message) {
            try (FileWriter fw = new FileWriter("chat_log.txt", true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter pw = new PrintWriter(bw)) {
                pw.println(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private JTextArea chatArea;
    private JTextField messageField;

    public Client() {
        JFrame frame = new JFrame("Чат клиент");
        chatArea = new JTextArea(20, 50);
        chatArea.setEditable(false);
        messageField = new JTextField(50);

        JButton sendButton = new JButton("Отправить");
        sendButton.addActionListener(e -> sendMessage());

        messageField.addActionListener(e -> sendMessage());

        JPanel panel = new JPanel();
        panel.add(new JScrollPane(chatArea));
        panel.add(messageField);
        panel.add(sendButton);

        frame.getContentPane().add(panel, BorderLayout.CENTER);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            loadChatHistory();

            new Thread(new IncomingMessagesHandler()).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String message = messageField.getText();
        if (!message.isEmpty()) {
            out.println(message);
            messageField.setText("");
        }
    }

    private void loadChatHistory() {
        try (BufferedReader br = new BufferedReader(new FileReader("chat_log.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                chatArea.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class IncomingMessagesHandler implements Runnable {
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    chatArea.append(message + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
