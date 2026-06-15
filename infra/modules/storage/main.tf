resource "azurerm_storage_account" "storage" {
  name                     = var.storage_account_name
  resource_group_name      = var.resource_group_name
  location                 = var.location
  account_tier             = "Standard"
  account_replication_type = "LRS"

  network_rules {
    default_action             = "Deny"
    virtual_network_subnet_ids = [var.subnet_id]
  }
}

resource "azurerm_storage_table" "url_mappings" {
  name                 = "UrlMappings"
  storage_account_name = azurerm_storage_account.storage.name
}

resource "azurerm_storage_table" "api_keys" {
  name                 = "ApiKeys"
  storage_account_name = azurerm_storage_account.storage.name
}

resource "azurerm_private_endpoint" "storage_pe" {
  name                = "${var.storage_account_name}-pe"
  location            = var.location
  resource_group_name = var.resource_group_name
  subnet_id           = var.subnet_id

  private_service_connection {
    name                           = "${var.storage_account_name}-psc"
    private_connection_resource_id = azurerm_storage_account.storage.id
    subresource_names              = ["table"]
    is_manual_connection           = false
  }
}
