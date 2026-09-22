variable "allowed_api_cidr" {
  description = "Your public IPv4 address as a /32 CIDR, for example 203.0.113.10/32."
  type        = string

  validation {
    condition     = can(cidrhost(var.allowed_api_cidr, 0)) && endswith(var.allowed_api_cidr, "/32")
    error_message = "Use a single IPv4 address in /32 CIDR form."
  }
}

variable "container_image" {
  description = "Backend image URI. The service starts with zero tasks until the app has an AWS-compatible configuration."
  type        = string
  default     = "public.ecr.aws/docker/library/eclipse-temurin:21-jre"
}
