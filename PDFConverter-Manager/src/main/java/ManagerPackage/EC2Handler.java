package ManagerPackage;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

public class EC2Handler {
    private static final Region REGION = Region.US_EAST_1;
    public static Ec2Client ec2c = Ec2Client.builder().region(REGION).build();
    private static String workerAmiName = "WorkerAmi";
    private static String workerAmiId = "ami-00e95a9222311e8ed";
    
    public static String createEC2WorkerInstance() {
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(workerAmiId)
                .instanceType(InstanceType.T2_MICRO)
                .maxCount(1)
                .minCount(1)
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build())
                .userData(Base64.getEncoder().encodeToString(("#!/bin/bash\n" +
                "export AWS_ACCESS_KEY_ID=ASIASNDMA53H3VKDD3V4\n" +
                "export AWS_SECRET_ACCESS_KEY=uocMF0vjU6Ov4G/HhyXSkMSEvF6bDdQ9YKW1Q9jX\n" +
                "export AWS_SESSION_TOKEN=FwoGZXIvYXdzEPL//////////wEaDHVy7Mag9JvWKy1awSLIASxc5Jep9YwRBuXipwpfj5/WzTSzZ2sbu+OEB5eMeDcx9fzdoj0ZdmK0Ll80jU8F9GCWyB8+BpeW+W0FEmbhBpkCCRcAj6zQk6YC7VUJ8CnFR59JsoOXUb05XItqk7+n3jVxiX8DdZUee5coMGhu6ZCxOai18VfpHRe7JTuCdJY0kdn59stoLsS0LbuvLO4awtAtsRLSZFjAmA4X/rLGRs7E5Qn8ZgJSDf6Tzs7v3MVpABi71aVpmaJecIEy1OFnys0GvNR7zXtZKOvewY0GMi3HsnFF63dS5NokilQcGcul3lXPBg5d2n2xtvkYSh+osmeIk+DUP2eNqfsESMk=\n" +
                "export AWS_DEFAULT_REGION=us-east-1\n" +
                "aws s3 cp s3://liormaxim1234/worker.jar /home/ec2-user/worker.jar\n" +
                "cd home/ec2-user\n" + 
                "java -jar worker.jar\n").getBytes()))
                .build();
        RunInstancesResponse response = ec2c.runInstances(runRequest);
        String instanceId = response.instances().get(0).instanceId();
        Tag tag = Tag.builder()
                .key("worker")
                .value(workerAmiName)
                .build();
        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();
        ec2c.createTags(tagRequest);
        return instanceId;
    }

    private static String getManagerRunningId() {
        String nextToken = null;

        do {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
            DescribeInstancesResponse response = ec2c.describeInstances(request);

            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    for (Tag tag : instance.tags()) {
                        if (tag.key().equals("manager")) {
                            return instance.instanceId();
                        }
                    }
                }
            }
            nextToken = response.nextToken();
        } while (nextToken != null);

        return null;
    }

    public static void terminateManager(){
        String managerId = getManagerRunningId();
        if(managerId != null){
            Tag tag = Tag.builder()
                    .key("manager")
                    .build();

            DeleteTagsRequest deleteTagsRequest = DeleteTagsRequest.builder().resources(EC2Handler.getManagerRunningId()).tags(tag).build();
            EC2Handler.ec2c.deleteTags(deleteTagsRequest);
            terminateInstance(managerId);
        }
    }

    public static void terminateAllWorkers() {
        List<String> workersInstances = new LinkedList<>();

        String nextToken = null;
        do {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
            DescribeInstancesResponse response = ec2c.describeInstances(request);

            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    for (Tag tag : instance.tags()) {
                        if (tag.key().equals("worker")) {
                            workersInstances.add(instance.instanceId());
                        }
                    }
                }
            }
            nextToken = response.nextToken();
        } while (nextToken != null);

        terminateInstances(workersInstances);
    }

    private static void terminateInstances(List<String> instanceId){
        TerminateInstancesRequest terminateInstancesRequest = TerminateInstancesRequest.builder().instanceIds(instanceId).build();
        ec2c.terminateInstances(terminateInstancesRequest);
    }

    private static void terminateInstance(String instanceId){
        TerminateInstancesRequest terminateInstancesRequest = TerminateInstancesRequest.builder().instanceIds(instanceId).build();
        ec2c.terminateInstances(terminateInstancesRequest);
    }
}
