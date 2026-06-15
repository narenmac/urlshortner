resource "azurerm_redis_cache" "redis" {
  name                 = var.redis_name
  location             = var.location
  resource_group_name  = var.resource_group_name
  capacity             = var.capacity
  family               = var.sku_name == "Premium" ? "P" : "C"
  sku_name             = var.sku_name
  minimum_tls_version  = "1.2"
  non_ssl_port_enabled = false

  redis_configuration {
    maxmemory_policy = "allkeys-lru"
  }
}

resource "azurerm_private_endpoint" "redis_pe" {
  name                = "${var.redis_name}-pe"
  location            = var.location
  resource_group_name = var.resource_group_name
  subnet_id           = var.subnet_id

  private_service_connection {
    name                           = "${var.redis_name}-psc"
    private_connection_resource_id = azurerm_redis_cache.redis.id
    subresource_names              = ["redisCache"]
    is_manual_connection           = false
  }
}
