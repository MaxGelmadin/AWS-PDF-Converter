package WorkerPackage;

import java.io.*;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;


public class S3Handler {

    private static final Region REGION = Region.US_EAST_1;
    private static S3Client s3c = S3Client.builder().region(REGION).build();

    public static void createBucket(String bucketName) {
        s3c.createBucket(CreateBucketRequest
                .builder()
                .bucket(bucketName)
                .createBucketConfiguration(
                        CreateBucketConfiguration.builder()
                                .build())
                .build());
    }

    public static void deleteBucket(String bucketName) {
        DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName).build();
        s3c.deleteBucket(deleteBucketRequest);
    }

    public static void UploadFile(String bucketName, String keyName, String fileName){
        s3c.putObject(PutObjectRequest.builder().bucket(bucketName).key(keyName).build(), RequestBody.fromFile(new File(fileName)));
    }

    public static BufferedReader downloadFile(String bucketName, String key){
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        ResponseInputStream<GetObjectResponse> s3objectResponse = s3c.getObject(getObjectRequest);
        return new BufferedReader(new InputStreamReader(s3objectResponse));
    }

    public static void deleteObject(String bucketName, String key){
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3c.deleteObject(deleteObjectRequest);
    }

    public static void CleanAndRemoveBucket(String bucketName) {
        try {
            // To delete a bucket, all the objects in the bucket must be deleted first
            ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder().bucket(bucketName).build();
            ListObjectsV2Response listObjectsV2Response;

            do {
                listObjectsV2Response = s3c.listObjectsV2(listObjectsV2Request);
                for (S3Object s3Object : listObjectsV2Response.contents()) {
                    deleteObject(bucketName, s3Object.key());
                }

                listObjectsV2Request = ListObjectsV2Request.builder().bucket(bucketName)
                        .continuationToken(listObjectsV2Response.nextContinuationToken())
                        .build();

            } while(listObjectsV2Response.isTruncated());

            deleteBucket(bucketName);

        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

}
