output "aks_cluster_name" {
  value = module.aks.cluster_name
}

output "aks_resource_group" {
  value = var.resource_group_name
}

output "storage_connection_string" {
  value     = module.storage.connection_string
  sensitive = true
}

output "redis_hostname" {
  value = module.redis.hostname
}

output "redis_primary_key" {
  value     = module.redis.primary_key
  sensitive = true
}

output "vnet_id" {
  value = module.vnet.vnet_id
}
