FROM 080266302756.dkr.ecr.us-west-2.amazonaws.com/openjdk:latest
MAINTAINER abdelali.jadelmoula
COPY target/demo-0.0.1-SNAPSHOT.jar message-server.jar
ENTRYPOINT ["java","-jar","/message-server.jar"]