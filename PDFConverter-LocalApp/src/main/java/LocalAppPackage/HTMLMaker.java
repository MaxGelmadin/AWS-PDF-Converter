package LocalAppPackage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class HTMLMaker {

    public static void make(String output, BufferedReader reader) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(output + ".html"));

        writer.write("<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <style>\n" +
                "       table, th, td {\n" +
                "       border: 1px solid black;\n" +
                "       border-collapse: collapse;\n" +
                "       }\n" +
                "    </style>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>PDF Converter Output</title>\n" +
                "    <div class=\"header\">\n" +
                "        <h1>PDF Converter Output</h1>\n" +
                "    </div>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "  <table>\n" +
                "      <tr>\n" +
                "          <th>Operation</th>\n" + //TODO changed names
                "          <th>Input File</th>\n" +
                "          <th>Output File</th>\n" +
                "      </tr>\n");

        String line;
        while ((line = reader.readLine()) != null) {
            writer.write(line);
            writer.newLine();
        }

        writer.write(
                "  </table>\n" +
                        "</body>\n" +
                        "</html>");

        writer.close();
    }
}
