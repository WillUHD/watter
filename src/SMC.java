import java.io.*;

public class SMC {

    public static Process process;

    public static InputStream send(String command) {
        try {
            ProcessBuilder builder = new ProcessBuilder("zsh", "-c", command);
            builder.redirectErrorStream(true);
            process = builder.start();
            return process.getInputStream();
        } catch (IOException e) {throw new RuntimeException(e);}
    }

    public static String start(String line) {
        var pb = new ProcessBuilder("zsh", "-c", line);
        pb.redirectErrorStream(true);
        var sb = new StringBuilder();
        try {
            var p = pb.start();
            var reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String output;
            while ((output = reader.readLine()) != null) sb.append(output).append('\n');
            p.waitFor();
        } catch (IOException | InterruptedException ignored) {}
        return sb.toString().trim();
    }
}
