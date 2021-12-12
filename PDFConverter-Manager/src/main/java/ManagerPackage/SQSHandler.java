package ManagerPackage;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

public class SQSHandler {

    private static final Region REGION = Region.US_EAST_1;
    public static SqsClient sqsc = SqsClient.builder().region(REGION).build();

    public static void createQueue(String queueName){
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(queueName)
                .build();
        sqsc.createQueue(createQueueRequest);
    }

    public static void sendMessage(String queueName, String Message){
        sqsc.sendMessage(SendMessageRequest.builder()
                .queueUrl(getQueueUrl(queueName))
                .messageBody(Message)
                .delaySeconds(10)
                .build());
    }

    private static String getQueueUrl(String queueName){
        GetQueueUrlResponse getQueueUrlResponse =
                sqsc.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
        String queueUrl = getQueueUrlResponse.queueUrl();
        return queueUrl;
    }

    public static List<Message> receiveMessages(String queueName){
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(getQueueUrl(queueName))
                .maxNumberOfMessages(5)
                .build();
        List<Message> messages = sqsc.receiveMessage(receiveMessageRequest).messages();
        return messages;
    }

    public static List<Message> receiveMessage(String queueName){
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(getQueueUrl(queueName))
                .maxNumberOfMessages(1)
                .build();
        return sqsc.receiveMessage(receiveMessageRequest).messages();
    }

    public static void deleteMessage(String queueName, Message message){
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(getQueueUrl(queueName))
                .receiptHandle(message.receiptHandle())
                .build();
        sqsc.deleteMessage(deleteMessageRequest);
    }

    public static void deleteMessages(String queueName, List<Message> messages){
        for (Message message : messages) {
            deleteMessage(queueName, message);
        }
    }

    public static void deleteQueue(String queueName) {
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();
        String queueUrl = sqsc.getQueueUrl(getQueueRequest).queueUrl();
        DeleteQueueRequest deleteQueueRequest = DeleteQueueRequest.builder()
                .queueUrl(queueUrl)
                .build();
        sqsc.deleteQueue(deleteQueueRequest);
    }
}
