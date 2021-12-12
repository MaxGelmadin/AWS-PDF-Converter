package ManagerPackage;

import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.regions.Region;

public class Manager {
    //todo debug
    //static int counter = 0;

    // We restrict the max number of instances so that amazon will not restrict us
    private static final int MAX_ALLOWED_WORKERS = 15;

    private static int activeWorkers = 0;
    private static boolean shouldTerminate = false;

    // Hashmap that hold a key for every LocalApp, correlating to its relevant hashmap that hold a key for every file,
    // correlating to its relevant outputValues class
    //private static ConcurrentHashMap<String, ConcurrentHashMap<String, OutputValues>> LocalAppJob = new ConcurrentHashMap<>();
    private static int jobsNumber = 0;
    private static int jobsCompleted = 0;
    private static File outputFile;  //final file to send to s3

    public static void main(String[] args) {
        outputFile = new File("output.txt"); // not yet created
        SQSHandler.createQueue("Manager2Workers");
        SQSHandler.createQueue("Workers2Manager");

        // create a new thread for handling local app messages
        Thread handleMessageFromLocalApp = new Thread(() -> {
            while (!shouldTerminate){
                try {
                    receiveAndHandleMessageFromLocalApp();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            SQSHandler.deleteQueue("LocalApp2Manager");
        });
        handleMessageFromLocalApp.start();

        // create a new thread for handling workers messages
        Thread handleMessageFromWorkers = new Thread(() -> {
            //while (!shouldTerminate || !LocalAppJob.isEmpty()){ // TODO find better way then hashtable
            while (!shouldTerminate || (jobsNumber>jobsCompleted)){
                receiveAndHandleMessageFromWorkers();

            }

            EC2Handler.terminateAllWorkers();

            SQSHandler.deleteQueue("Workers2Manager");
            SQSHandler.deleteQueue("Manager2Workers");

            EC2Handler.terminateManager();
        });
        handleMessageFromWorkers.start();

        try {
            handleMessageFromLocalApp.join();
            handleMessageFromWorkers.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void receiveAndHandleMessageFromLocalApp() throws IOException {
        List<Message> LocalApp2ManagerMessages = getMessages("LocalApp2Manager");
        for (Message message : LocalApp2ManagerMessages) {
            //System.out.println(counter++ + ") Received message from LocalApp: " + message.body() + "\n"); //TODO debug
            if(message.body().equals("terminate")){
                shouldTerminate = true;
            }
            else {
                String[] messageArgs = message.body().split(",");

                String Manager2LocalAppQueue = messageArgs[0];
                String bucket = messageArgs[1];
                String inputFileKey = messageArgs[2];
                int numberOfPDFsPerWorker = Integer.parseInt(messageArgs[3]);

                assignJobs(S3Handler.downloadFile(bucket, inputFileKey), Manager2LocalAppQueue, bucket, inputFileKey, numberOfPDFsPerWorker);
                S3Handler.deleteFile(bucket, inputFileKey);

                // Create empty file
                try {
                    if (outputFile.createNewFile()) {
                        System.out.println("File created: " + outputFile.getName());
                    } else {
                        System.out.println("File already exists.");
                        outputFile.delete();
                        outputFile.createNewFile();
                    }
                } catch (IOException e) {
                    System.out.println("An error occurred.");
                    e.printStackTrace();
                }

            }
            SQSHandler.deleteMessage("LocalApp2Manager", message);
        }
    }

    private static void assignJobs(BufferedReader reader, String Manager2LocalAppQueue, String bucket, String inputFileKey, int numberOfPDFsPerWorker) throws IOException {
        String outputFileKey = "outputKey";

        String line = reader.readLine();
        while (line != null) {
            if(!line.isEmpty()) {
                if (isNewWorkerNeeded(jobsNumber, numberOfPDFsPerWorker)) {
                    EC2Handler.createEC2WorkerInstance();
                    activeWorkers++;
                }
                String[] operationAndURL = line.split("\t"); // [operation, URL]
                SQSHandler.sendMessage("Manager2Workers", Manager2LocalAppQueue + "," + outputFileKey + "," + jobsNumber + "," + operationAndURL[0] + "," + bucket + "," + operationAndURL[1]);
                //System.out.println(counter++ + ") Sent a message to Worker: " + Manager2LocalAppQueue + "," + outputFileKey + "," + jobsNumber + "," + operationAndURL[0] + "," + bucket + "," + operationAndURL[1] + "\n"); //todo debug
                line = reader.readLine();
                jobsNumber++;
            }
        }
    }

    // checks if activeWorkers are under the max value we specified and that enough workers are running according to the
    // necessary amount of workers per n messages
    private static boolean isNewWorkerNeeded(int count, int numberOfPDFsPerWorker){
        return activeWorkers <= MAX_ALLOWED_WORKERS && count >= activeWorkers * numberOfPDFsPerWorker;
    }

    //
    private static void receiveAndHandleMessageFromWorkers(){
        List<Message> Workers2ManagerMessages = getMessages("Workers2Manager");
        for (Message message : Workers2ManagerMessages) {
            String[] messageArgs = message.body().split(",");
            System.out.println("Received message from worker: " + message.body());
            String Manager2LocalAppQueue = messageArgs[0];
            String jobNumber = messageArgs[1];
            String bucket = messageArgs[2];
            String subTableHTML = messageArgs[3];
            String outputFileKey = "converted" + jobNumber;
            try{
                FileOutputStream fos = new FileOutputStream(outputFile, true);
                fos.write(subTableHTML.getBytes(StandardCharsets.UTF_8));
                fos.close();
            }
            catch (Exception e){
                e.printStackTrace();
            }
            jobsCompleted++;
            if(jobsCompleted == jobsNumber){
                System.out.println("Job completed. Uploading the outputFile.\n");
                S3Handler.UploadFile(bucket, "outputKey", outputFile.getAbsolutePath());
                SQSHandler.sendMessage(Manager2LocalAppQueue, "outputKey");
            }
        }
        SQSHandler.deleteMessages("Workers2Manager", Workers2ManagerMessages);
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