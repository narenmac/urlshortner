package com.urlshortener.service.repository;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.models.TableEntity;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public class UrlRepository {

    private static final String TABLE_NAME = "UrlMappings";

    @Value("${azure.storage.connection-string}")
    private String connectionString;

    private TableClient tableClient;

    @PostConstruct
    public void init() {
        tableClient = new TableClientBuilder()
                .connectionString(connectionString)
                .tableName(TABLE_NAME)
                .buildClient();
        tableClient.createTableIfNotExists();
    }

    public void save(String code, String longUrl) {
        String partitionKey = code.substring(0, 2);
        TableEntity entity = new TableEntity(partitionKey, code);
        entity.addProperty("LongUrl", longUrl);
        entity.addProperty("CreatedAt", OffsetDateTime.now().toString());
        entity.addProperty("HitCount", 0L);
        tableClient.upsertEntity(entity);
    }

    public String findByCode(String code) {
        String partitionKey = code.substring(0, 2);
        try {
            TableEntity entity = tableClient.getEntity(partitionKey, code);
            return entity.getProperty("LongUrl").toString();
        } catch (Exception e) {
            return null;
        }
    }
}
