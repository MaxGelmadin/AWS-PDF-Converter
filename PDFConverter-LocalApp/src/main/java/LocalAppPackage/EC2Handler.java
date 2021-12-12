package LocalAppPackage;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class EC2Handler {
    private static final Region REGION = Region.US_EAST_1;
    public static Ec2Client ec2c = Ec2Client.builder().region(REGION).build();
    private static String managerAmiName = "ManagerAmi";
    private static String managerAmiId = "ami-00e95a9222311e8ed";

    public static boolean isManagerRunning(){
        String managerState = getManagerState().nameAsString();
        return (managerState.equals("pending") || managerState.equals("running"));
    }


    /**
     * About the 'createQueue' param:
     * This function is called from 2 different places in LocalApp.java:
     * -    The first call checks if the first Manager should be initialized and since this is the first Manager during this run
     *      a brand-new queue must be created, and thus this function is invoked with 'createQueue = true'.
     * -    The second call happens while LocalApp waits for the 'end' message of the Manager. While waiting we check regularly
     *      that the Manager is still alive, if not - this function is invoked where 'createQueue = false' and thus a new Manager is
     *      initialized - continuing the job that the crushed Manager hasn't finished from the same queue.
     */
    public static String validateManagerRunning (boolean createQueue){
        String managerInstanceId = getManagerRunningId();
        //System.out.println("manager id: " + managerInstanceId); todo debug
        if(managerInstanceId == null){
            System.out.println("Initializing manager");
            //create queue from manager
            if (createQueue)
                SQSHandler.createQueue("LocalApp2Manager");

            //start manager
            return EC2Handler.createEC2ManagerInstance(managerAmiName, managerAmiId);
        }
        return managerInstanceId;
    }

    private static String createEC2ManagerInstance(String name, String amiId) {
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(InstanceType.T2_MEDIUM)
                .maxCount(1)
                .minCount(1)
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build())
                .userData(Base64.getEncoder().encodeToString(("#!/bin/bash\n" +
                "export AWS_ACCESS_KEY_ID=ASIASNDMA53H3VKDD3V4\n" +
                "export AWS_SECRET_ACCESS_KEY=uocMF0vjU6Ov4G/HhyXSkMSEvF6bDdQ9YKW1Q9jX\n" +
                "export AWS_SESSION_TOKEN=FwoGZXIvYXdzEPL//////////wEaDHVy7Mag9JvWKy1awSLIASxc5Jep9YwRBuXipwpfj5/WzTSzZ2sbu+OEB5eMeDcx9fzdoj0ZdmK0Ll80jU8F9GCWyB8+BpeW+W0FEmbhBpkCCRcAj6zQk6YC7VUJ8CnFR59JsoOXUb05XItqk7+n3jVxiX8DdZUee5coMGhu6ZCxOai18VfpHRe7JTuCdJY0kdn59stoLsS0LbuvLO4awtAtsRLSZFjAmA4X/rLGRs7E5Qn8ZgJSDf6Tzs7v3MVpABi71aVpmaJecIEy1OFnys0GvNR7zXtZKOvewY0GMi3HsnFF63dS5NokilQcGcul3lXPBg5d2n2xtvkYSh+osmeIk+DUP2eNqfsESMk=\n" +
                "export AWS_DEFAULT_REGION=us-east-1\n" +
                "aws s3 cp s3://liormaxim1234/manager.jar /home/ec2-user/manager.jar\n" +
                "cd home/ec2-user\n" + 
                "java -jar manager.jar\n").getBytes()))
                .build();
        RunInstancesResponse response = ec2c.runInstances(runRequest);
        String instanceId = response.instances().get(0).instanceId();
        Tag tag = Tag.builder()
                .key("manager")
                .value(name)
                .build();
        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();
        ec2c.createTags(tagRequest);
        System.out.printf(
                "Successfully started EC2 Instance %s based on AMI %s\n",
                instanceId, amiId);
        return instanceId;
    }

    public static String getManagerRunningId() {
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

    private static InstanceState getManagerState() {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().instanceIds(getManagerRunningId()).build();
        DescribeInstancesResponse response = ec2c.describeInstances(request);

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                return instance.state();
            }
        }

        return null;
    }

    public static void terminateAllInstances() {
        List<String> Instances = new LinkedList<>();

        String nextToken = null;
        do {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
            DescribeInstancesResponse response = ec2c.describeInstances(request);

            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {

                    Instances.add(instance.instanceId());
                    for (Tag tag : instance.tags()) {
                        if (tag.key().equals("manager")) {
                            DeleteTagsRequest deleteTagsRequest = DeleteTagsRequest.builder().resources(instance.instanceId()).tags(tag).build();
                            EC2Handler.ec2c.deleteTags(deleteTagsRequest);
                        }
                    }
                }
            }
            nextToken = response.nextToken();
        } while (nextToken != null);
        terminateInstances(Instances);

    }
    private static void terminateInstances(List<String> instanceId){
        TerminateInstancesRequest terminateInstancesRequest = TerminateInstancesRequest.builder().instanceIds(instanceId).build();
        ec2c.terminateInstances(terminateInstancesRequest);
    }
}