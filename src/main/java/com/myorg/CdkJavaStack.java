package com.myorg;
import com.myorg.Conf;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateServiceProps;
import software.amazon.awscdk.services.ecs.AwsLogDriverMode;
import software.amazon.awscdk.services.ecs.AwsLogDriver;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.route53.*;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.secretsmanager.*;
import software.amazon.awscdk.services.certificatemanager.*;
import software.amazon.awscdk.services.s3.*;
import software.amazon.awscdk.StackProps;
import java.util.*;
import org.json.JSONObject;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class CdkJavaStack extends Stack {
    public CdkJavaStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CdkJavaStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        
        Conf conf = new Conf();
    
        // Create a VPC
        Vpc vpc = Vpc.Builder.create(this, "EcsVpc")
                .vpcName(conf.prefix + "-" + conf.vpc_name)
                .maxAzs(1) // 1 Availability Zone
                .cidr(conf.vpc_cidr)
                .enableDnsSupport(true)
                .enableDnsHostnames(true)
                .subnetConfiguration(List.of(
                        SubnetConfiguration.builder()
                                .name("Public")
                                .subnetType(SubnetType.PUBLIC)
                                .build(),
                        SubnetConfiguration.builder()
                                .name("Isolated1")
                                .subnetType(SubnetType.PRIVATE_ISOLATED)
                                .build(),
                         SubnetConfiguration.builder()
                                .name("Isolated2")
                                .subnetType(SubnetType.PRIVATE_ISOLATED)
                                .build()
                ))
                .build();

        // Create an ECS Cluster
        Cluster cluster = Cluster.Builder.create(this, "ECSCluster")
                .clusterName(conf.prefix + "-" + conf.clustername)
                .vpc(vpc)
                .build();
        // ECS task execution role, is used by the ecs agent
        
        
        // ECS task role, is used by the task itself
        Role ecstaskrole = Role.Builder.create(this, "EcsTaskRole")
         .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
         .description("ecs task role")
         .build();
         
        // Create a Fargate task definition
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, "ECSTaskDefinition")
                .cpu(1024) // Set the desired CPU units
                .memoryLimitMiB(2048) // Set the desired memory limit in MiB
                .family(conf.prefix + "-" + conf.taskname)
                .taskRole(ecstaskrole)
                .build();
        
        taskDefinition.addToExecutionRolePolicy(PolicyStatement.Builder
           .create()
           .actions(List.of("ecr:getauthorizationtoken",
                "ecr:batchchecklayeravailability",
                "ecr:getdownloadurlforlayer",
                "ecr:batchgetimage",
                "logs:createlogstream",
                "logs:putlogevents"))
           .effect(Effect.ALLOW)
           .resources(List.of("*"))
           .build()
        );
        
        // Add a container to the task definition for the Spring Boot app
        taskDefinition.addContainer("SpringBootContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry(conf.ecr_repo)) // Replace with your Docker image
                .portMappings(List.of(PortMapping.builder()
                 .containerPort(conf.app_port)
                 .protocol(Protocol.TCP)
                 .build()))
                .logging(AwsLogDriver.Builder.create().streamPrefix(conf.logstream).mode(AwsLogDriverMode.NON_BLOCKING).build())
                .memoryReservationMiB(2048)
                .build()
        );
                
        // Retrieve the existing hosted zone in Route 53
        IHostedZone hostedZone = HostedZone.fromHostedZoneAttributes(this, "HostedZone",
                 HostedZoneAttributes
                 .builder()
                .zoneName(conf.zonename) // Replace with your custom domain
                .hostedZoneId(conf.zoneid)
                .build()
        );
        
        
        // Ecs Service SecurityGroup
        SecurityGroup servicesg = new SecurityGroup(this, "EcsServiceSG", 
             SecurityGroupProps
             .builder()
            .allowAllOutbound(true)
            .securityGroupName(conf.prefix + "-" + conf.sg_name)
            .vpc(vpc)
            .build()
        );
        
        servicesg.addIngressRule(
          Peer.ipv4(vpc.getVpcCidrBlock()),
          Port.tcp(conf.app_port)
        );
    
        
        // Use the ECS Network Load Balanced Fargate Service 
        NetworkLoadBalancer nlb = new NetworkLoadBalancer(this, "NewtorkLoadbalancer", 
           NetworkLoadBalancerProps
           .builder()
           .internetFacing(true)
           .loadBalancerName(conf.prefix + "-" + conf.loadbalancername)
           .vpc(vpc)
           .vpcSubnets(SubnetSelection.builder()
                 .subnetType(SubnetType.PUBLIC)
                 .build())
           .build()
        );
        
        // NLB Target Group
        NetworkTargetGroup target = new NetworkTargetGroup(this, "NLBTargetGroup", 
              NetworkTargetGroupProps
             .builder()
             .port(conf.app_port)
             .targetType(TargetType.IP)
             .vpc(vpc)
             .build()
        );
        
        // NLB listener
        NetworkListener listener = nlb.addListener("ssllistener",
             BaseNetworkListenerProps
            .builder()
            .certificates(List.of(ListenerCertificate.fromArn(conf.certificate)))
            .port(443)
            .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TLS)
            .build()
        );
        
        listener.addTargetGroups("TargetListener", target);
        
        // ECS Service
        FargateService fargateservice = new FargateService(this,"EcsService",
            FargateServiceProps
            .builder()
            .assignPublicIp(true)
            .cluster(cluster)
            .serviceName(conf.prefix + "-" + conf.servicename)
            .desiredCount(conf.desiredcount)
            .taskDefinition(taskDefinition)
            .securityGroups(List.of(servicesg))
            .vpcSubnets(SubnetSelection.builder()
                     .subnetType(SubnetType.PUBLIC)
                     .build())
            .build()
        );
        
        fargateservice.attachToNetworkTargetGroup(target);
      
        // Create a Route 53 A record pointing to the NLB
        RecordTarget nlbAliasRecordTarget = RecordTarget.fromAlias(new LoadBalancerTarget(nlb));
        ARecord nlbAliasRecord = ARecord.Builder.create(this, "AliasRecord")
                .zone(hostedZone)
                .target(nlbAliasRecordTarget)
                .recordName(conf.recordname)
                .build();
        
        // Templated secret with username and password fields
        Secret templatedSecret = Secret.Builder.create(this, "TemplatedSecret")
                 .secretName(conf.prefix + "-" + conf.secretname)
                 .generateSecretString(SecretStringGenerator.builder()
                         .secretStringTemplate(new JSONObject(Map.of("username", conf.rdsusername)).toString())
                         .generateStringKey("password")
                         .build())
                 .build();
                 
        // Rds security group
        // Ecs Service SecurityGroup
        SecurityGroup rdssg = new SecurityGroup(this, "AWSRDSSG", 
             SecurityGroupProps
             .builder()
            .allowAllOutbound(true)
            .securityGroupName(conf.prefix + "-" + "rdssg")
            .vpc(vpc)
            .build()
        );
        
        rdssg.addIngressRule(
          Peer.ipv4(vpc.getVpcCidrBlock()),
          Port.tcp(5432)
        );
        // Using the templated secret as credentials
        DatabaseInstance instance = DatabaseInstance.Builder.create(this, "PostgresInstance")
                 .engine(DatabaseInstanceEngine.POSTGRES)
                 .credentials(Credentials.fromSecret(templatedSecret))
                 .databaseName(conf.rdsdb)
                 .securityGroups(List.of(rdssg))
                 .instanceIdentifier(conf.prefix + "-" + conf.rdsinstanceidentifier)
                 .allocatedStorage(20) // Specify storage size in GB
                 .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.MICRO))
                 .multiAz(false)
                 .vpcSubnets(SubnetSelection.builder()
                     .subnetType(SubnetType.PRIVATE_ISOLATED)
                     .build())
                 .vpc(vpc)
                 .build();
        
        // S3 bucket
        BucketProps bucketProps = BucketProps.builder()
                .versioned(true)
                .removalPolicy(RemovalPolicy.DESTROY) // Change to RETAIN if you want to prevent accidental deletion
                .autoDeleteObjects(true) // Change to false if you want to retain objects when the bucket is deleted
                .build();

        Bucket bucket = new Bucket(this, "MyBucket", bucketProps);
        


        
    }
}
