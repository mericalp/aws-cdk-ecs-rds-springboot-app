# create s3 bucket using aws cli 

```
aws s3api create-bucket \
    --bucket aws-cdk-codecommit-07-11-2022 \
    --region us-west-2 \
    --create-bucket-configuration LocationConstraint=us-west-2
    
```

# enable versioning 

```

aws s3api put-bucket-versioning \
    --bucket aws-cdk-codecommit-07-11-2022 \
    --versioning-configuration Status=Enabled
```

# zip the project

```
zip -r application.zip . -x "*.git*"
```

# cp the the zip file to the s3 bucket created earlier

```
aws s3 cp application.zip s3://aws-cdk-codecommit-07-11-2022
```
# aws get versions of object 

```
aws s3api list-object-versions --bucket aws-cdk-codecommit-07-11-2022
```