package com.smartbudget;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"openai.api-key=test-key",
		"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.com/test-issuer",
		"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.com/test-jwks",
		"aws.region=ap-southeast-2",
		"aws.s3.bucket=S3_BUCKET_NAME"
})
class SmartbudgetBackendApplicationTests {

	@Test
	void contextLoads() {
	}
}