package com.myorg;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import com.myorg.Conf;


public class CdkJavaApp {
    public static void main(final String[] args) {
        App app = new App();
        Conf conf = new Conf();

        new CdkJavaStack(app, "CdkJavaStack", StackProps.builder()
                .env(Environment.builder()
                        .account(conf.accountId)
                        .region(conf.region)
                        .build())

                // For more information, see https://docs.aws.amazon.com/cdk/latest/guide/environments.html
                .build());

        app.synth();
    }
}

