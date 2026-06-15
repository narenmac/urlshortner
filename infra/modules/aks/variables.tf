variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "cluster_name" { type = string }
variable "dns_prefix" { type = string }
variable "frontend_subnet_id" { type = string }
variable "backend_subnet_id" { type = string }
variable "node_vm_size_frontend" { type = string }
variable "node_vm_size_backend" { type = string }
