package WorkerPackage;

import com.aspose.pdf.Document;
import com.aspose.pdf.SaveFormat;
import com.aspose.pdf.TextExtractionOptions;
import com.aspose.pdf.devices.PngDevice;
import com.aspose.pdf.devices.Resolution;
import com.aspose.pdf.devices.TextDevice;
import com.aspose.pdf.exceptions.InvalidPdfFileFormatException;
import software.amazon.awssdk.services.sqs.model.Message;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;


public class Worker {
    static int counter = 0;
    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String[] args) {
        while (true) {
            List<Message> messages = getMessages("Manager2Workers");
            for (Message message : messages) {
                System.out.println(counter++ + ") Received message from the Manager: " + message.body() + "\n");
                String[] messageArgs = message.body().split(",");
                //TODO: remove legacy variables
                String Manager2LocalAppQueue = messageArgs[0];
                String outputFileKey = messageArgs[1];
                String jobNumber = messageArgs[2];
                String operation = messageArgs[3];
                String bucket = messageArgs[4];
                String url = messageArgs[5];

                StringBuilder subTableHTML = new StringBuilder();
                String uploadedURL = null;

                try {
                    System.out.println("Downloading pdf..." );
                    InputStream in = new URL(url).openStream();
                    //TODO: I don't really know if this is an eligible path
                    Files.copy(in, Paths.get("/home/ec2-user/pdf" + jobNumber + ".pdf"), StandardCopyOption.REPLACE_EXISTING);
                    Document pdfDocument = new Document("/home/ec2-user/pdf" + jobNumber + ".pdf");
                    if (operation.equals("ToImage")) {
                        OutputStream imageStream = new FileOutputStream("/home/ec2-user/converted" + jobNumber + ".png");
                        Resolution resolution = new Resolution(300);
                        // Create PngDevice object with particular resolution
                        PngDevice pngDevice = new PngDevice(resolution);
                        // Convert a particular page and save the image to stream
                        pngDevice.process(pdfDocument.getPages().get_Item(1), imageStream);
                        // Close the stream
                        imageStream.close();
                    } else if (operation.equals("ToHTML")) {
                        pdfDocument.save("/home/ec2-user/converted" + jobNumber + ".html", SaveFormat.Html);
                    } else {
                        TextDevice textDevice = new TextDevice();
                        TextExtractionOptions textExtOptions = new TextExtractionOptions(TextExtractionOptions.TextFormattingMode.Raw);
                        textDevice.setExtractionOptions(textExtOptions);
                        textDevice.process(pdfDocument.getPages().get_Item(1), "/home/ec2-user/converted" + jobNumber + ".txt");
                    }
                    // Uploading the converted file to S3
                    String suffix = operation.equals("ToImage") ? ".png" :
                                    operation.equals("ToHTML") ? ".html" :
                                    ".txt";
                    String convertedName = "converted" + jobNumber + suffix;
                    System.out.println("Uploading the converted file to: " + bucket);
                    S3Handler.UploadFile(bucket, convertedName, convertedName);
                    uploadedURL = "https://" + bucket + ".s3.us-east-1.amazonaws.com/" + convertedName;
                    sbAppend(subTableHTML, null, operation, url, uploadedURL);
                } catch (IOException | InvalidPdfFileFormatException e) {
                    System.out.println("Caught an exception: " + e);
                    sbAppend(subTableHTML, e, operation, url, uploadedURL);
                }
                SQSHandler.sendMessage("Workers2Manager",Manager2LocalAppQueue +  "," + jobNumber + "," + bucket + "," + subTableHTML);
                System.out.println(counter++ + ") Sent message to Manager: " + Manager2LocalAppQueue +  "," + jobNumber + "," + bucket + "," + subTableHTML);
            }
            SQSHandler.deleteMessages("Manager2Workers", messages);
        }
    }

    private static void sbAppend(StringBuilder sb, Exception e, String operation, String url, String uploadedURL) {
        if (e == null) {
            sb.append(
                    "\n    <tr>\n" +
                            "          <th>" + operation + "</th>\n" +
                            "          <th>" + url + "</th>\n" +
                            "          <th>" + uploadedURL + "</th>\n" +
                            "      </tr>");
        }
        else {
            String exceptionName = e.getClass().getSimpleName();
            sb.append(
                    "\n    <tr>\n" +
                            "          <th>" + operation + "</th>\n" +
                            "          <th>" + url + "</th>\n" +
                            "          <th>" + exceptionName + "</th>\n" +
                            "      </tr>");
        }
    }

    private static List<Message> getMessages(String queueName) {
        List<Message> messages;
        int dotCount = 0;
        while (true) {
            messages = SQSHandler.receiveMessages(queueName);
            int size = messages.size();
            if (size != 0) {
                return messages;
            }
            dotCount++;
            System.out.print(".");
            if (dotCount == 30) {
                dotCount = 0;
                System.out.println();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("Caught Interrupted Exception = " + e);
            }
        }
    }
}

