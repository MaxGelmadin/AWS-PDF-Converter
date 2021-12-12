package ManagerPackage;

import java.io.*;
import java.util.Collection;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;


public class S3Handler {

    private static final Region REGION = Region.US_EAST_1;
    private static S3Client s3c = S3Client.builder().region(REGION).build();

    public static void UploadFile(String bucketName, String keyName, String fileName){
        s3c.putObject(PutObjectRequest.builder().bucket(bucketName).key(keyName).build(), RequestBody.fromFile(new File(fileName)));
    }

    public static void sendCompleteMultipartUpload(CompleteMultipartUploadRequest completeMultipartUploadRequest){
        s3c.completeMultipartUpload(completeMultipartUploadRequest);
    }

    public static void createBucket(String bucketName) {
        s3c.createBucket(CreateBucketRequest
                .builder()
                .bucket(bucketName)
                .createBucketConfiguration(
                        CreateBucketConfiguration.builder()
                                .build())
                .build());
    }

    public static String startMultipartUpload(String bucketName, String key){
        CreateMultipartUploadRequest createMultipartUploadRequest =
                CreateMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
        CreateMultipartUploadResponse response =
                s3c.createMultipartUpload(createMultipartUploadRequest);
        return response.uploadId();
    }

    public static CompletedPart addPartToMultipartUpload(String bucketName, String key, String uploadId, int partNumber, String message){
        UploadPartRequest uploadPartRequest1 = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber).build();
        String etag1 = s3c.uploadPart(uploadPartRequest1,
                RequestBody.fromString(message)).eTag();
        return CompletedPart.builder().partNumber(partNumber).eTag(etag1).build();
    }

    public static CompleteMultipartUploadRequest getCompleteMultipartUploadRequest(String bucketName, String key, String uploadId, Collection<CompletedPart> parts){
        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                .parts(parts)
                .build();
        return CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(completedMultipartUpload)
                .build();
    }

    public static BufferedReader downloadFile(String bucketName, String key){
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        ResponseInputStream<GetObjectResponse> s3objectResponse = s3c.getObject(getObjectRequest);
        return new BufferedReader(new InputStreamReader(s3objectResponse));
    }

    public static void deleteFile(String bucketName, String key){
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3c.deleteObject(deleteObjectRequest);
    }

}
