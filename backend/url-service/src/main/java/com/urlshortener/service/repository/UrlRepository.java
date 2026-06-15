package com.urlshortener.service.repository;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
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
        TableServiceClient serviceClient = new TableServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        serviceClient.createTableIfNotExists(TABLE_NAME);
        tableClient = serviceClient.getTableClient(TABLE_NAME);
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
