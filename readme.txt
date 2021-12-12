##Running Instructions:
-> open the cmd in PDFConverter folder
-> run the following command:
	>java -jar LocalApp.jar <inputfile.txt> <output> n [terminate]
-> where:
	* <inputfile.txt> is a place holder for the desired input file with all the operations and URLs separated by a tab
	* <output> - place holder for the location of the output file
	* "n" - how many PDFs each worker will process
	* "terminate" - a flag for terminating the manager

##Program description:

--- >LocalApp.jar ---

-> initiation:
	* checks if termination flag was entered to determine whether to shut-down the manager at the end
	* creates a bucket in S3 which is dedicated for the upload of the input file, and receive the output file
	* create a SQS queue uniqely named "Manager2LocalApp" + System.currentTimeMillis()
	* validate that the manager is running, if not, create a SQS queue named "LocalApp2Manager" and create the EC2 instance of the manager with does settings:
		- using the AMI image "ManagerAmi" with ID "ami-00e95a9222311e8ed" of type "T2_MEDIUM", it comes with java support
		- using IamInstanceProfileSpecification named "LabInstanceProfile" that allows to use the neccesary AWS services.
		- the manager initialization runs a bash script in the following way:
			1. setting the right credentials to allow access to aws's services
			2. using "aws s3 cp" command to download the jar file from the bucket and copying it to the local machine
			3. using "cd" command to redirect the bash to the location of the jar file
			4. using "java -jar" command to run the manager's application

-> uploads the input files to S3
-> send to a queue named "LocalApp2Manager" a message that states:
	* the name of the unique queue relevant to the LocalApp
	* the name of the unique bucket relevant to the LocalApp
	* the name of the input file
	* the number of PDFs that each worker will process

-> in case of an error in the upload, it concludes that the manager received a termination message by another LocalApp and therefore the "LocalApp2Manager" queue is no longer available. LocalApp stops sending other files.

-> read from the "Manager2LocalApp" queue messages about file completion for each file that was read successfully by the manager.
	* here we are waiting for the message from the Manager so we can download the output file (expanded below).
	  while waiting we check each 100 iterations if the Manager is still alive, if not - we create a new one.

-> upon receiving a message, the message includes the output file name in S3 and therefore downloads the file. every line in the file is in HTML format that fit a line in a table, HTMLMaker is a class that chain those lines together and make the HTML output file with the named that was specified in the initial arguments.

-> delete the output file from S3.

-> while trying to receive messages from the Manager, check if the manger has crushed, and if so exit from the while-loop and close all necessary AWS services. wait 60 seconds so there wouldn't be a SQS error if the user will try to run it again. 

-> if it didn't crush, but the manager was terminated through another LocalApp, then delete all unrecived files from the S3 bucket.

-> if the manager is still running, all the files have been downloaded successfully and the termination argument was received - send a termination message to the manager through the LocalApp2Manager queue.

-> delete "Manager2LocalAppQueue" from the SQS and the relating bucket in S3.

-> finish:
	-> when the LocalApp received the last message from the Manager:
		* the output file is being downloaded to your local machine (specific to the root folder)
		* terminate all the worker and manager instances
		* delete all queues
--- /LocalAPP.jar ---


--- >Manager.jar ---

-> creates "Manager2Workers" and "Workers2Manager" SQS queues

-> starts 2 Threads, one for handling messages from LocalApp and sending jobs to workers, and one for handling messages from Workers and sending completion messages to LocalApp

-> handleMessageFromLocalApp Thread:
	-> gets messages from "LocalApp2Manager" queue

	-> checks if the message is "terminate", if so stop receiving any other messages from this queue, else continue:

	-> downloads the input file from S3. every line of the file has the following skeleton: <operation>'\t'<URL> , send it to the "Manager2Workers" as a message that states:
		* the name of the uniqely queue relevant to this file
		* the name of the input file
		* the number of this job
		* the line after separation

	-> checks if there are sufficient workers as defined by the number PDFs per worker, and if not initiate another worker instance with these settings:
		- using the AMI image "WorkerAmi" with ID "ami-00e95a9222311e8ed" of type "T2_MICRO", it installed with java
		- using IamInstanceProfileSpecification named "LabInstanceProfile" that allows to use the neccesary AWS services.
		- each worker initialization runs a bash script in the following way:
			1. setting the right credentials to allow access to aws's services
			2. using "aws s3 cp" command to download the jar file from the bucket and copying it to the local machine
			3. using "cd" command to redirect the bash to the location of the jar file
			4. using "java -jar" command to run the worker's application



-> handleMessageFromWorkers Thread:
	* as long as the manager didn't receive a termination message or there are still messages from the workers:
	* get messages from "Worker2Manager" queue
	* check if the correlating file is not already completed (beacuse duplication of jobs output may occure if two workers reads the same message from the SQS queue)
	* using the subTableHTML caten all the HTML outputs received from the workers 
	* if the number of initial jobs is equal to the number of the finished jobs:
		* upload to S3 the "outputKey" which contains all the catenated HTML values
		* send to the LocalApp a message via "Manager2LocalApp" queue that it is done to process the PDFs
--- /Manager.jar ---

--- Worker.jar ---
-> receive a message from "Manager2Workers" SQS queue

-> process the PDFs with the following style:
	* connect to the URL sent by the manager
	* download the PDF and save it on the local space
	* convert the PDF to the relevant file type (.png, .html, .txt)
	** upon success of all the above - upload the file to S3, then send a message to the manager via "Worker2Manager" queue with a detailed subTableHTML:
	   subTableHTML looks like this:
				"\n    <tr>\n" +
                            	"          <th>" + operation + "</th>\n" +
                           	"          <th>" + url + "</th>\n" +
                            	"          <th>" + uploadedURL + "</th>\n" +
                            	"      </tr>"
	* note that an exception might occurr, in this case the subTableHTML will have a short description of the exception - instead of the "uploadedURL".

-> send the string to the "Worker2Manager" queue


--- /Worker.jar ---