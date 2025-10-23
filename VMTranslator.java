import java.io.*;
import java.util.*;

public class VMTranslator {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Uso: java VMTranslator <archivo.vm | carpeta>");
            System.exit(1);
        }

        File input = new File(args[0]);
        if (!input.exists()) {
            System.err.println("No existe: " + input.getAbsolutePath());
            System.exit(2);
        }

        List<File> vmFiles = new ArrayList<>();
        File outputAsm;

        if (input.isDirectory()) {
            for (File f : Objects.requireNonNull(input.listFiles())) {
                if (f.getName().endsWith(".vm")) {
                    vmFiles.add(f);
                }
            }
            outputAsm = new File(input, input.getName() + ".asm");
        } else {
            vmFiles.add(input);
            String baseName = input.getAbsolutePath().substring(0, input.getAbsolutePath().lastIndexOf('.'));
            outputAsm = new File(baseName + ".asm");
        }

        try (CodeWriter writer = new CodeWriter(outputAsm)) {
            // Siempre inicializamos SP=256 y llamamos Sys.init
            writer.writeInit();

            for (File f : vmFiles) {
                String name = f.getName().replace(".vm", "");
                writer.setFileName(name);
                try (Parser parser = new Parser(f)) {
                    while (parser.hasMoreCommands()) {
                        parser.advance();
                        switch (parser.commandType()) {
                            case C_ARITHMETIC:
                                writer.writeArithmetic(parser.arg1());
                                break;
                            case C_PUSH:
                                writer.writePushPop("push", parser.arg1(), parser.arg2());
                                break;
                            case C_POP:
                                writer.writePushPop("pop", parser.arg1(), parser.arg2());
                                break;
                            case C_LABEL:
                                writer.writeLabel(parser.arg1());
                                break;
                            case C_GOTO:
                                writer.writeGoto(parser.arg1());
                                break;
                            case C_IF:
                                writer.writeIf(parser.arg1());
                                break;
                            case C_FUNCTION:
                                writer.writeFunction(parser.arg1(), parser.arg2());
                                break;
                            case C_CALL:
                                writer.writeCall(parser.arg1(), parser.arg2());
                                break;
                            case C_RETURN:
                                writer.writeReturn();
                                break;
                        }
                    }
                }
            }

            System.out.println("Traducción completada: " + outputAsm.getAbsolutePath());
        }
    }
}
