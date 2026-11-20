package com.dailyreading;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** Runs the same HTTP/persistence cases against an explicitly supplied isolated MySQL. */
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL", matches=".+")
@SpringBootTest(properties={
    "spring.profiles.active=demo",
    "spring.datasource.url=${MYSQL_TEST_URL}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.datasource.username=root",
    "spring.datasource.password=",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=file:./sql/init.sql",
    "app.crypto.key-file=target/test-secrets/mysql.key"
})
class MysqlDeliveryIntegrationTest extends DeliveryIntegrationTest {
    @Override @org.junit.jupiter.api.Test void schemaCanRepeatWithoutLosingData() {
        Long id=examples.load(user);
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.FileSystemResource("sql/init.sql"))
                .execute(java.util.Objects.requireNonNull(jdbc.getDataSource()));
        org.junit.jupiter.api.Assertions.assertNotNull(articles.findById(id,user));
    }
}
