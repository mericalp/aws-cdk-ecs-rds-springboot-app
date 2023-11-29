package com.myorg;
import java.util.*;

public class Conf {
    
    public String prefix = "dev";
    
    // CDK Conf
    public String accountId = "";
    public String region    = "us-east-2";
    
    // Network Conf
    public String vpc_name = "ecs-vpc";
    public String vpc_cidr = "20.10.0.0/16";
    public String sg_name  = "ecservicesg";
    
    // Domain conf
    public String certificate = "";
    public String zonename    = "abdelalitraining.com";
    public String zoneid      = "";
    public String recordname  = "";
    public String loadbalancername = "ecsservice-nlb";
    public boolean stickness = false;
    
    
    // ECS Configuration
    public String clustername = "ecscluster";
    public String servicename = "springservice";
    public String taskname    = "springtask";
    public String ecr_repo = "";
    public int app_port    = 8080;
    public int desiredcount = 2;
    public String logstream = "springbootlogs";
    
    // DB Conf
    public String secretname = "rdssecret";
    public String rdsdb = "abdelali";
    public String rdsusername = "abdelali";
    public String rdsinstanceidentifier = "rdsinstance";
    
    // S3 bucket
    
    public String bucketname = "";
    
    
}
