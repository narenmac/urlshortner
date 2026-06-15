output "vnet_id" {
  value = azurerm_virtual_network.vnet.id
}

output "frontend_subnet_id" {
  value = azurerm_subnet.frontend.id
}

output "backend_subnet_id" {
  value = azurerm_subnet.backend.id
}

output "resource_group_name" {
  value = azurerm_resource_group.rg.name
}
