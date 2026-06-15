output "connection_string" {
  value     = azurerm_storage_account.storage.primary_connection_string
  sensitive = true
}

output "storage_account_id" {
  value = azurerm_storage_account.storage.id
}
