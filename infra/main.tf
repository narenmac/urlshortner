terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.100"
    }
  }
  required_version = ">= 1.5.0"

  backend "azurerm" {
    resource_group_name  = "tfstate-rg"
    storage_account_name = "tfstateurlshortener"
    container_name       = "tfstate"
    key                  = "url-shortener.tfstate"
  }
}

provider "azurerm" {
  features {}
}

module "vnet" {
  source                 = "./modules/vnet"
  resource_group_name    = var.resource_group_name
  location               = var.location
  vnet_name              = var.vnet_name
  vnet_address_space     = var.vnet_address_space
  frontend_subnet_prefix = var.frontend_subnet_prefix
  backend_subnet_prefix  = var.backend_subnet_prefix
}

module "storage" {
  source               = "./modules/storage"
  resource_group_name  = var.resource_group_name
  location             = var.location
  storage_account_name = var.storage_account_name
  subnet_id            = module.vnet.backend_subnet_id
}

module "redis" {
  source              = "./modules/redis"
  resource_group_name = var.resource_group_name
  location            = var.location
  redis_name          = var.redis_name
  subnet_id           = module.vnet.backend_subnet_id
  sku_name            = var.redis_sku
  capacity            = var.redis_capacity
}

module "aks" {
  source                 = "./modules/aks"
  resource_group_name    = var.resource_group_name
  location               = var.location
  cluster_name           = var.aks_cluster_name
  dns_prefix             = var.aks_dns_prefix
  frontend_subnet_id     = module.vnet.frontend_subnet_id
  backend_subnet_id      = module.vnet.backend_subnet_id
  node_vm_size_frontend  = var.node_vm_size_frontend
  node_vm_size_backend   = var.node_vm_size_backend
}
