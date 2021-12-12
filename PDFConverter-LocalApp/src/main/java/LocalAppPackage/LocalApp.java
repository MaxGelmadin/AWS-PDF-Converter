package LocalAppPackage;

import software.amazon.awssdk.services.sqs.model.Message;

import java.io.*;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class LocalApp {
    public static void main(String[] args) throws IOException, InterruptedException {
        boolean shouldTerminate = false;
        String numberOfPDFsPerWorker = args[2];

        long startTimeMillis = System.currentTimeMillis();
        long startTimeMinutes = TimeUnit.MILLISECONDS.toMinutes(startTimeMillis);
        // get argument
        if(args.length == 4 && args[3].equals("terminate"))
            shouldTerminate = true;
        else if( args.length != 3){
            System.out.println("Invalid number of Arguments");
            System.exit(1);
        }

        String bucket = "bucket" + System.currentTimeMillis();
        String Manager2LocalAppQueue = "Manager2LocalApp" + System.currentTimeMillis();

        //Initialize a bucket in EC2
        S3Handler.createBucket(bucket);

        //Initialize the Queues in SQS
        SQSHandler.createQueue(Manager2LocalAppQueue);

        //todo debug return this
        //create manager
        EC2Handler.validateManagerRunning(true);
        //SQSHandler.createQueue("LocalApp2Manager");

        //TODO changed upload
        System.out.println("\nStarted uploading the files...\n");
        //uploads the files

        String inputFileKey = "inputKey";
        S3Handler.UploadFile(bucket, inputFileKey, args[0]);
        System.out.println("Uploaded file: " + args[0]);
        try {
            System.out.println("Sending this message to the manager:\n" +  Manager2LocalAppQueue + "," + bucket + "," + inputFileKey + "," + numberOfPDFsPerWorker + "\n");
            SQSHandler.sendMessage("LocalApp2Manager", Manager2LocalAppQueue + "," + bucket + "," + inputFileKey + "," + numberOfPDFsPerWorker); //TODO last param
            System.out.println("\nThe message was sent through \"LocalApp2Manager\" queue. The manager should initialize workers.\nPress enter to continue");
            new Scanner(System.in).nextLine();
        }
        catch (Exception e) {
            //Manager has received a terminate message and LocalApp2Manager queue is no longer exist
            S3Handler.deleteObject(bucket, inputFileKey);
            System.out.println("Exception occurred: " + e);
        }

        System.out.println("\nWaiting for completion...\n");
        //wait for completion
        boolean managerCrushed = false;
        System.out.println("Blocking the main thread. Busy waiting for Manager to finish\n");
        List<Message> messageList = getMessages(Manager2LocalAppQueue);

        int counter = 0;
        for(Message message : messageList){//TODO for probably not needed since we receive only a single message
            System.out.println("Received message from manager: " + message.body());
            System.out.printf("Message number %d being processed\n", counter++);
            String outputFileKey = message.body(); //TODO the key of the summary file

            //download summary file
            HTMLMaker.make(args[1], S3Handler.downloadFile(bucket, outputFileKey));
            System.out.println("Downloaded file: " + outputFileKey + " successfully");
            //delete file in S3
            S3Handler.deleteObject(bucket, outputFileKey);
        }


        //delete message from Manager2LocalApp Queue
        SQSHandler.deleteMessages(Manager2LocalAppQueue, messageList);

        if(!EC2Handler.isManagerRunning()){
            //check if the manager crashed
            if(SQSHandler.isQueueAvailable("LocalApp2Manager")) { // manager was supposed to delete queue
                managerCrushed = true;
            }
        }

        // !managerCrushed
        if(!managerCrushed){

            if(shouldTerminate){
                SQSHandler.sendMessage("LocalApp2Manager", "terminate");
                Thread.sleep(2000);
                EC2Handler.terminateAllInstances();
                System.out.println("\nManager Instance terminated");
            }

            //delete Queuesm.out.println("Deleting queue: " + Manager2LocalAppQueue);
            System.out.println("\nDeleting queue: " + Manager2LocalAppQueue);
            SQSHandler.deleteQueue(Manager2LocalAppQueue);

            // delete the input file, so it is possible to delete the bucket
            System.out.println("\nDeleting object: " + inputFileKey);           
            S3Handler.deleteObject(bucket, inputFileKey);

            long endTimeMillis = System.currentTimeMillis();
            long endTimeMinutes = TimeUnit.MILLISECONDS.toMinutes(endTimeMillis);

            System.out.println("\nIt took the program: " + (endTimeMinutes - startTimeMinutes) + " minutes to finish");
        }

        else {
            EC2Handler.terminateAllInstances();
            S3Handler.CleanAndRemoveBucket(bucket);
            SQSHandler.deleteQueue(Manager2LocalAppQueue);
            try {
                SQSHandler.deleteManagerQueues();
            }
            catch (Exception e){
                //
            }


            System.out.println("\nManager Instance crushed, closed all AWS services and waiting 60 sec...");

            Thread.sleep(60000);

            System.out.println("\nExiting. Run the program again");

            System.exit(1);
        }
    }

    private static List<Message> getMessages(String queueName) {
        List<Message> messages;
        int dotCount = 0, checkIfManagerIsAlive = 0;
        while (true) {
            messages = SQSHandler.receiveMessages(queueName);
            int size = messages.size();
            if (size != 0) {
                return messages;
            }
            dotCount++; checkIfManagerIsAlive++;
            System.out.print(".");
            if (dotCount == 30) {
                dotCount = 0;
                System.out.println();
            }
            if (checkIfManagerIsAlive == 100) {
                checkIfManagerIsAlive = 0;
                EC2Handler.validateManagerRunning(false);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("Caught Interrupted Exception = " + e);
            }
        }
    }
}
