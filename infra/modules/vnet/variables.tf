variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "vnet_name" { type = string }
variable "vnet_address_space" { type = list(string) }
variable "frontend_subnet_prefix" { type = list(string) }
variable "backend_subnet_prefix" { type = list(string) }
