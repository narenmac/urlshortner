variable "resource_group_name" {
  description = "Name of the Azure resource group"
  type        = string
  default     = "url-shortener-rg"
}

variable "location" {
  description = "Azure region"
  type        = string
  default     = "eastus"
}

variable "vnet_name" {
  description = "Name of the virtual network"
  type        = string
  default     = "url-shortener-vnet"
}

variable "vnet_address_space" {
  description = "Address space for VNet"
  type        = list(string)
  default     = ["10.0.0.0/16"]
}

variable "frontend_subnet_prefix" {
  description = "CIDR for frontend subnet"
  type        = list(string)
  default     = ["10.0.1.0/24"]
}

variable "backend_subnet_prefix" {
  description = "CIDR for backend subnet"
  type        = list(string)
  default     = ["10.0.2.0/24"]
}

variable "storage_account_name" {
  description = "Storage account name (must be globally unique)"
  type        = string
  default     = "urlshortenerstore"
}

variable "redis_name" {
  description = "Redis cache name"
  type        = string
  default     = "url-shortener-redis"
}

variable "redis_sku" {
  description = "Redis SKU name"
  type        = string
  default     = "Standard"
}

variable "redis_capacity" {
  description = "Redis capacity (C1=1GB, C2=2.5GB, etc.)"
  type        = number
  default     = 1
}

variable "aks_cluster_name" {
  description = "AKS cluster name"
  type        = string
  default     = "url-shortener-aks"
}

variable "aks_dns_prefix" {
  description = "AKS DNS prefix"
  type        = string
  default     = "urlshortener"
}

variable "node_vm_size_frontend" {
  description = "VM size for frontend node pool"
  type        = string
  default     = "Standard_D2s_v3"
}

variable "node_vm_size_backend" {
  description = "VM size for backend node pool"
  type        = string
  default     = "Standard_D4s_v3"
}
