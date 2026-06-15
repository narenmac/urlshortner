resource "azurerm_kubernetes_cluster" "aks" {
  name                = var.cluster_name
  location            = var.location
  resource_group_name = var.resource_group_name
  dns_prefix          = var.dns_prefix

  default_node_pool {
    name           = "system"
    node_count     = 2
    vm_size        = "Standard_D2s_v3"
    vnet_subnet_id = var.frontend_subnet_id
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin = "azure"
    service_cidr   = "10.1.0.0/16"
    dns_service_ip = "10.1.0.10"
  }
}

resource "azurerm_kubernetes_cluster_node_pool" "frontend" {
  name                  = "frontend"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.aks.id
  vm_size               = var.node_vm_size_frontend
  vnet_subnet_id        = var.frontend_subnet_id

  enable_auto_scaling = true
  min_count           = 2
  max_count           = 5
  node_count          = 2

  node_labels = {
    "tier" = "frontend"
  }
}

resource "azurerm_kubernetes_cluster_node_pool" "backend" {
  name                  = "backend"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.aks.id
  vm_size               = var.node_vm_size_backend
  vnet_subnet_id        = var.backend_subnet_id

  enable_auto_scaling = true
  min_count           = 2
  max_count           = 10
  node_count          = 3

  node_labels = {
    "tier" = "backend"
  }
}
