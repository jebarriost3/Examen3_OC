import java.io.*;
import java.util.*;

public class Parser implements Closeable {

    public enum CommandType {
        C_ARITHMETIC, C_PUSH, C_POP, C_LABEL, C_GOTO, C_IF, C_FUNCTION, C_CALL, C_RETURN
    }

    private BufferedReader reader;
    private String currentCommand;
    private String arg1;
    private int arg2;
    private CommandType currentType;

    public Parser(File file) throws IOException {
        reader = new BufferedReader(new FileReader(file));
    }

    private String cleanLine(String line) {
        if (line.contains("//")) {
            line = line.substring(0, line.indexOf("//"));
        }
        return line.trim();
    }

    public boolean hasMoreCommands() throws IOException {
    reader.mark(10000);
    String line;
    while ((line = reader.readLine()) != null) {
        line = cleanLine(line);
        if (!line.isEmpty()) {
            reader.reset();
            reader.mark(10000); 
            return true;
        }
        reader.mark(10000);
    }
    return false;
}


    public void advance() throws IOException {
        String line;
        while (true) {
            line = reader.readLine();
            if (line == null) {
                currentCommand = null;
                return;
            }
            line = cleanLine(line);
            if (!line.isEmpty()) {
                currentCommand = line;
                parseCommand(line);
                return;
            }
        }
    }

    private void parseCommand(String line) {
        String[] parts = line.split("\\s+");
        String cmd = parts[0];

        switch (cmd) {
            case "push":
                currentType = CommandType.C_PUSH;
                arg1 = parts[1];
                arg2 = Integer.parseInt(parts[2]);
                break;
            case "pop":
                currentType = CommandType.C_POP;
                arg1 = parts[1];
                arg2 = Integer.parseInt(parts[2]);
                break;
            case "label":
                currentType = CommandType.C_LABEL;
                arg1 = parts[1];
                break;
            case "goto":
                currentType = CommandType.C_GOTO;
                arg1 = parts[1];
                break;
            case "if-goto":
                currentType = CommandType.C_IF;
                arg1 = parts[1];
                break;
            case "function":
                currentType = CommandType.C_FUNCTION;
                arg1 = parts[1];
                arg2 = Integer.parseInt(parts[2]);
                break;
            case "call":
                currentType = CommandType.C_CALL;
                arg1 = parts[1];
                arg2 = Integer.parseInt(parts[2]);
                break;
            case "return":
                currentType = CommandType.C_RETURN;
                break;
            default:
                currentType = CommandType.C_ARITHMETIC;
                arg1 = cmd; // add, sub, eq, gt
                break;
        }
    }

    public CommandType commandType() {
        return currentType;
    }

    public String arg1() {
        return arg1;
    }

    public int arg2() {
        return arg2;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
