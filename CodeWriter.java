import java.io.*;

public class CodeWriter implements Closeable {

    private BufferedWriter writer;
    private String currentFunction = "";
    private int labelCounter = 0;
    private int returnCounter = 0;
    private String fileName = "";

    public CodeWriter(File outputFile) throws IOException {
        writer = new BufferedWriter(new FileWriter(outputFile));
    }

    public void setFileName(String name) {
        this.fileName = name;
    }

    private void writeLine(String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    // Bootstrap code: SP=256 y call Sys.init
    public void writeInit() throws IOException {
        writeLine("// bootstrap");
        writeLine("@256");
        writeLine("D=A");
        writeLine("@SP");
        writeLine("M=D");
        writeCall("Sys.init", 0);
    }

    // Traducción de comandos aritméticos
    public void writeArithmetic(String command) throws IOException {
        writeLine("// " + command);
        switch (command) {
            case "add":
               binaryOp("M=D+M");
                break;
            case "sub":
                binaryOp("M=M-D");
                break;
            case "eq":
                compareOp("JEQ");
                break;
            case "gt":
                compareOp("JGT");
                break;
            default:
                throw new RuntimeException("Comando aritmético no soportado: " + command);
        }
    }

    // Operaciones binarias
    private void binaryOp(String operation) throws IOException {
        popToD();
        writeLine("@SP");
        writeLine("A=M-1");
        writeLine(operation);
    }

    // Comparaciones (eq, gt)
    private void compareOp(String jump) throws IOException {
        String TRUE = "TRUE_" + labelCounter;
        String END = "END_" + labelCounter;
        labelCounter++;

        popToD(); // D = y
        writeLine("@SP");
        writeLine("A=M-1"); // A = x
        writeLine("D=M-D"); // x - y
        writeLine("@" + TRUE);
        writeLine("D;" + jump);
        // false case
        writeLine("@SP");
        writeLine("A=M-1");
        writeLine("M=0");
        writeLine("@" + END);
        writeLine("0;JMP");
        // true case
        writeLine("(" + TRUE + ")");
        writeLine("@SP");
        writeLine("A=M-1");
        writeLine("M=-1");
        writeLine("(" + END + ")");
    }

    // push D en la pila
    private void pushD() throws IOException {
        writeLine("@SP");
        writeLine("A=M");
        writeLine("M=D");
        writeLine("@SP");
        writeLine("M=M+1");
    }

    // pop a D
    private void popToD() throws IOException {
        writeLine("@SP");
        writeLine("AM=M-1");
        writeLine("D=M");
    }

    // push segment index
    private void pushFromSegment(String segmentPointer, int index) throws IOException {
        writeLine("@" + segmentPointer);
        writeLine("D=M");
        writeLine("@" + index);
        writeLine("A=D+A");
        writeLine("D=M");
        pushD();
    }

    // pop into segment
    private void popToSegment(String segmentPointer, int index) throws IOException {
        writeLine("@" + segmentPointer);
        writeLine("D=M");
        writeLine("@" + index);
        writeLine("D=D+A");
        writeLine("@R13");
        writeLine("M=D");
        popToD();
        writeLine("@R13");
        writeLine("A=M");
        writeLine("M=D");
    }

    // Push/pop handler
    public void writePushPop(String command, String segment, int index) throws IOException {
        writeLine("// " + command + " " + segment + " " + index);
        if (command.equals("push")) {
            switch (segment) {
                case "constant":
                    writeLine("@" + index);
                    writeLine("D=A");
                    pushD();
                    break;
                case "local":
                    pushFromSegment("LCL", index);
                    break;
                case "argument":
                    pushFromSegment("ARG", index);
                    break;
                default:
                    throw new RuntimeException("Segmento no soportado: " + segment);
            }
        } else if (command.equals("pop")) {
            switch (segment) {
                case "local":
                    popToSegment("LCL", index);
                    break;
                case "argument":
                    popToSegment("ARG", index);
                    break;
                default:
                    throw new RuntimeException("Segmento no soportado: " + segment);
            }
        }
    }

    // Labels
    public void writeLabel(String label) throws IOException {
        writeLine("(" + scoped(label) + ")");
    }

    public void writeGoto(String label) throws IOException {
        writeLine("@"+ scoped(label));
        writeLine("0;JMP");
    }

    public void writeIf(String label) throws IOException {
        popToD();
        writeLine("@"+ scoped(label));
        writeLine("D;JNE");
    }

    // Funciones
    public void writeFunction(String functionName, int numLocals) throws IOException {
        currentFunction = functionName;
        writeLine("(" + functionName + ")");
        for (int i = 0; i < numLocals; i++) {
            writeLine("@0");
            writeLine("D=A");
            pushD();
        }
    }

    public void writeCall(String functionName, int numArgs) throws IOException {
        String returnLabel = functionName + "$ret." + returnCounter++;
        writeLine("// call " + functionName);
        writeLine("@" + returnLabel);
        writeLine("D=A");
        pushD();

        pushSegmentPointer("LCL");
        pushSegmentPointer("ARG");
        pushSegmentPointer("THIS");
        pushSegmentPointer("THAT");

        writeLine("@SP");
        writeLine("D=M");
        writeLine("@" + (numArgs + 5));
        writeLine("D=D-A");
        writeLine("@ARG");
        writeLine("M=D");

        writeLine("@SP");
        writeLine("D=M");
        writeLine("@LCL");
        writeLine("M=D");

        writeLine("@" + functionName);
        writeLine("0;JMP");

        writeLine("(" + returnLabel + ")");
    }

    private void pushSegmentPointer(String pointer) throws IOException {
        writeLine("@" + pointer);
        writeLine("D=M");
        pushD();
    }

    public void writeReturn() throws IOException {
        writeLine("// return");
        writeLine("@LCL"); writeLine("D=M");
        writeLine("@R13"); writeLine("M=D");

        writeLine("@5"); writeLine("A=D-A"); writeLine("D=M");
        writeLine("@R14"); writeLine("M=D");

        popToD();
        writeLine("@ARG"); writeLine("A=M"); writeLine("M=D");

        writeLine("@ARG"); writeLine("D=M+1"); writeLine("@SP"); writeLine("M=D");

        restore("THAT", 1);
        restore("THIS", 2);
        restore("ARG", 3);
        restore("LCL", 4);

        writeLine("@R14");
        writeLine("A=M");
        writeLine("0;JMP");
    }

    private void restore(String segment, int offset) throws IOException {
        writeLine("@R13");
        writeLine("D=M");
        writeLine("@" + offset);
        writeLine("A=D-A");
        writeLine("D=M");
        writeLine("@" + segment);
        writeLine("M=D");
    }

    private String scoped(String label) {
        if (currentFunction.isEmpty()) return label;
        return currentFunction + "$" + label;
    }

    @Override
public void close() throws IOException {
    writer.flush(); 
    writer.close();
}
}
